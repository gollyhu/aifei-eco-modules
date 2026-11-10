/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.job.JobEvent;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调度内核：装配「唤醒层 + 执行层」两层线程池，并唯一持有续排投递入口 {@link #reschedule(JobTask, long)}。
 *
 * <p><b>两层职责严格分离</b>：唤醒层是 {@link ScheduledThreadPoolExecutor}（固定 1 线程，不暴露配置），只把到点的
 * 待触发实例提交给执行层，微秒级返回；执行层是 {@link ThreadPoolExecutor}（{@code job.pool.size} 线程 +
 * {@code job.pool.queue} 有界队列），跑任务体。这样「任务耗时」与「触发准时性」互不牵连，也避免任务体阻塞
 * 唤醒线程导致其它任务全部迟到。</p>
 *
 * <p><b>执行层拒绝策略固定 {@link ThreadPoolExecutor.AbortPolicy}</b>：队列满或已 {@code shutdown()} 时抛
 * {@link java.util.concurrent.RejectedExecutionException}，由 {@link JobWakeTask} 就地记账并重排。
 * 不允许 {@code CallerRunsPolicy}（会让任务体跑在唤醒线程上，拖垮全部任务）、不允许不抛异常的静默丢弃 handler
 * （会丢一次触发却无人知晓）、不允许 {@code put} 等阻塞提交（同样堵塞唤醒线程）。</p>
 *
 * <p><b>状态极简</b>：本类不保存任何任务的上次开始 / 结束时刻——它们是 {@link JobRunner} 的局部变量，随每次运行
 * 传递；因此不存在跨运行的可变节拍状态，也没有并发共享写。</p>
 */
public final class JobScheduler {

    /**
     * 唤醒线程名：固定 1 个线程，故不带序号。
     */
    private static final String WAKE_THREAD_NAME = "aifei-job-wake";

    /**
     * 执行层线程名前缀，后缀为从 1 开始的序号。
     */
    private static final String WORKER_THREAD_NAME_PREFIX = "aifei-job-worker-";

    /**
     * 配置值：线程数、队列容量、停机等待上限。
     */
    private final JobConfig config;

    /**
     * 任务注册表，{@link #start()} 据此排首批待触发实例。
     */
    private final JobRegistry registry;

    /**
     * 状态记账的唯一入口。
     */
    private final JobState state;

    /**
     * 事件装配与分发，供两个 Runnable 同步回调监听器。
     */
    private final JobEvents events;

    /**
     * 节拍计算纯函数。
     */
    private final JobSchedule schedule;

    /**
     * 本类日志：实例类在构造内取用，避免静态初始化期强取。
     */
    private final Log log;

    /**
     * 唤醒层。
     */
    private final ScheduledThreadPoolExecutor wake;

    /**
     * 执行层。
     */
    private final ThreadPoolExecutor pool;

    /**
     * 停机标志：置位后不再排任何新触发，{@link #reschedule(JobTask, long)} 一律放弃。
     */
    private volatile boolean stopping;

    /**
     * 是否已排过首批待触发实例，保证 {@link #start()} 幂等（重复排期会破坏「同一任务恒至多一个待触发实例」）。
     */
    private boolean started;

    /**
     * 装配两层线程池；构造期不创建任何线程（首次提交才创建），也不解析配置、不排期。
     *
     * @param config 配置值：线程数、队列容量、停机等待上限
     * @param registry 任务注册表
     * @param state 状态记账
     * @param events 事件装配与分发
     * @param schedule 节拍计算纯函数
     * @throws NullPointerException 任一参数为 {@code null}
     */
    public JobScheduler(JobConfig config, JobRegistry registry, JobState state, JobEvents events,
            JobSchedule schedule) {
        if (config == null || registry == null || state == null || events == null || schedule == null) {
            throw new NullPointerException("config, registry, state, events and schedule must not be null");
        }
        this.config = config;
        this.registry = registry;
        this.state = state;
        this.events = events;
        this.schedule = schedule;
        this.log = Log.get(JobScheduler.class);
        this.wake = newWakeExecutor();
        this.pool = new ThreadPoolExecutor(config.poolSize(), config.poolSize(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(config.poolQueue()),
                new JobThreadFactory(WORKER_THREAD_NAME_PREFIX, true),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 为每个任务排首个待触发实例；重复调用与已停机后调用都不做任何事。
     *
     * <p>首次排期只读一次时钟（本方法的 {@code now}）：{@code isFirst = true} 时 {@code JobSchedule} 对固定频率 /
     * 固定延迟返回 {@code initialDelay}、对 cron 返回 {@code scheduleNow} 之后的第一个表达式时刻，
     * {@code plannedTime} 由同一次采样推出，从而「首次延迟」只被消费一次。</p>
     */
    public synchronized void start() {
        if (started || stopping) {
            return;
        }
        started = true;
        long now = System.currentTimeMillis();
        for (JobTask task : registry.tasks()) {
            long delay = schedule.nextDelay(task, 0L, 0L, now, true);
            if (delay < 0L) {
                log.warn("job " + task.name() + " has no next fire time, scheduling abandoned");
                continue;
            }
            enqueue(task, now + delay, delay);
        }
    }

    /**
     * 停机收敛：停止新触发 → 等待在途任务 → 超时中断并记账 → 兜底清理唤醒层。
     *
     * <p>顺序固定（决策 9）：{@code stopping = true} → 唤醒层置「不执行已排队延迟任务」策略后 {@code shutdown()}
     * → 执行层 {@code shutdown()}（拒绝新提交、已提交任务继续执行）→ {@code awaitTermination} →
     * 超时则 {@code shutdownNow()} 并把返回的未执行任务逐个记 {@code SKIPPED}（事件时间取其原计划触发时刻）→
     * 唤醒层 {@code shutdownNow()} 兜底。唤醒层里<b>未到点</b>的待触发实例直接丢弃、不记账：它们的计划点尚未
     * 发生，属「停机期间错过」，不是「停机中断」。</p>
     *
     * <p>幂等：重复调用直接返回。方法结束后清除观测面装配，故此后 {@code JobKit} 取用返回空集合。</p>
     */
    public synchronized void stop() {
        if (stopping) {
            return;
        }
        stopping = true;
        // 必须先置策略再 shutdown：ScheduledThreadPoolExecutor#shutdown() 默认仍会执行已排队的延迟任务，
        // 那样「停止新触发」就无法达成（实现口径见决策 9）。
        wake.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        wake.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        wake.shutdown();
        pool.shutdown();
        boolean terminated = awaitPool();
        if (!terminated) {
            List<Runnable> dropped = pool.shutdownNow();
            skipDropped(dropped);
            log.warn("job pool did not terminate within " + config.shutdownTimeoutMillis() + " ms, "
                    + dropped.size() + " pending task(s) skipped");
        }
        wake.shutdownNow();
        JobHolder.clear();
    }

    /**
     * 续排投递的唯一入口：把「下次延迟」变成一个待触发实例投进唤醒层。
     *
     * <p>全模块只有两处调用：{@link JobRunner} 的 {@code finally}（正常路径）与 {@link JobWakeTask} 的
     * {@code catch}（提交被拒点）。缺后者时被拒任务进不了执行层，那个 {@code finally} 永远不执行，任务会永久停摆。</p>
     *
     * <p>投递时刻在此采样<b>一次</b>，{@code plannedTime = now + delay} 即该实例的「计划触发时刻」——它同时是
     * 后续被拒 / 停机两类 {@code SKIPPED} 事件的<b>事件时间</b>来源，因此必须是一个绝对时刻而不是延迟量。</p>
     *
     * @param task 任务定义
     * @param delay 距下次触发的毫秒数，由 {@link JobSchedule#nextDelay(JobTask, long, long, long, boolean)} 给出；
     *         为负表示「无下次触发」（如 cron 表达式永不再触发）
     * @return {@code true} 表示已投递；{@code false} 表示「无下次触发」而放弃（同时记一条 {@code warn}）
     * @throws java.util.concurrent.RejectedExecutionException 已停机（唤醒层 {@code shutdown()}）；
     *         由调用方就地静默终止并记「停机续排终止」，不新增记账点
     */
    public boolean reschedule(JobTask task, long delay) {
        // 停机后不在此处直接放弃：唤醒层已 shutdown()，投递必然抛 RejectedExecutionException，
        // 由两个调用点各自的 catch 记「停机续排终止」——「停止新触发」只有一条实现路径，没有第二套判断。
        if (delay < 0L) {
            log.warn("job " + task.name() + " has no next fire time, scheduling abandoned");
            return false;
        }
        long now = System.currentTimeMillis();
        enqueue(task, now + delay, delay);
        return true;
    }

    /**
     * 把一个执行单元提交给执行层；拒绝必须向上抛，由 {@link JobWakeTask} 的 {@code catch} 记账并重排。
     *
     * @param command 执行单元（恒为 {@link JobRunner}）
     * @throws java.util.concurrent.RejectedExecutionException 执行层队列已满或已 {@code shutdown()}
     */
    void execute(Runnable command) {
        pool.execute(command);
    }

    /**
     * @return 状态记账入口，供两个 Runnable 记终态
     */
    JobState state() {
        return state;
    }

    /**
     * @return 事件装配与分发入口，供两个 Runnable 同步回调监听器
     */
    JobEvents events() {
        return events;
    }

    /**
     * @return 节拍计算纯函数，供两个 Runnable 求下次延迟与超时跳过时刻
     */
    JobSchedule schedule() {
        return schedule;
    }

    /**
     * 创建唤醒层并固定两条停机策略。
     *
     * @return 唤醒层线程池（守护线程，固定 1 线程）
     */
    private static ScheduledThreadPoolExecutor newWakeExecutor() {
        ScheduledThreadPoolExecutor executor =
                new ScheduledThreadPoolExecutor(1, new JobThreadFactory(WAKE_THREAD_NAME, false));
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    /**
     * 投递一个待触发实例；本方法是 {@code wake.schedule(...)} 的唯一出现处。
     *
     * @param task 任务定义
     * @param plannedTime 计划触发时刻，epoch 毫秒
     * @param delay 距计划触发时刻的毫秒数，恒 {@code >= 0}
     * @throws java.util.concurrent.RejectedExecutionException 唤醒层已 {@code shutdown()}
     */
    private void enqueue(JobTask task, long plannedTime, long delay) {
        wake.schedule(new JobWakeTask(task, plannedTime, this), delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 等待在途任务结束；被中断时恢复中断位并视为「未在时限内结束」，交由 {@code shutdownNow()} 收尾。
     *
     * @return {@code true} 表示在 {@code job.shutdown.timeoutMillis} 内全部结束
     */
    private boolean awaitPool() {
        try {
            return pool.awaitTermination(config.shutdownTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 把 {@code shutdownNow()} 返回的未执行单元逐个记 {@code SKIPPED}。
     *
     * <p>事件时间取该单元的<b>原计划触发时刻</b>而不是当前时刻：这些单元的计划点已经到过（正因到点才被提交进
     * 执行层），用当前时刻描述「本该在那一刻执行却因停机中断」会让事件时间晚于停机基准，破坏倒挂防护。
     * 无法取到计划时刻时回落到当前时刻。</p>
     *
     * @param pending 未执行的单元清单
     */
    private void skipDropped(List<Runnable> pending) {
        for (Runnable dropped : pending) {
            if (!(dropped instanceof JobRunner)) {
                log.warn("job pool dropped an unknown runnable during shutdown: " + dropped);
                continue;
            }
            JobRunner runner = (JobRunner) dropped;
            long plannedTime = runner.plannedTime();
            long eventTime = plannedTime > 0L ? plannedTime : System.currentTimeMillis();
            JobEvent event = JobEvents.assemble(JobEvent.Phase.SKIPPED, runner.task().name(), eventTime, null);
            state.record(event);
            events.dispatch(event);
        }
    }

    /**
     * 线程工厂：两层线程一律守护线程，并带可辨识的名字。
     *
     * <p>守护线程使「插件未被 {@code stop()} 时不拖住 JVM 退出」成立；停机收敛仍由 {@link #stop()} 负责。</p>
     */
    private static final class JobThreadFactory implements ThreadFactory {

        /**
         * 线程名前缀。
         */
        private final String prefix;

        /**
         * 是否追加从 1 开始的序号。
         */
        private final boolean numbered;

        /**
         * 线程序号计数器。
         */
        private final AtomicInteger counter = new AtomicInteger();

        /**
         * @param prefix 线程名前缀
         * @param numbered 是否追加序号
         */
        JobThreadFactory(String prefix, boolean numbered) {
            this.prefix = prefix;
            this.numbered = numbered;
        }

        /**
         * @param runnable 线程主体
         * @return 已命名并置为守护的线程
         */
        @Override
        public Thread newThread(Runnable runnable) {
            String name = numbered ? prefix + counter.incrementAndGet() : prefix;
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
