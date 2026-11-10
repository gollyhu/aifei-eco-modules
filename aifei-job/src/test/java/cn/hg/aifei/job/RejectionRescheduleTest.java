/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.job.internal.JobEvents;
import cn.hg.aifei.job.internal.JobHolder;
import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobSchedule;
import cn.hg.aifei.job.internal.JobScheduler;
import cn.hg.aifei.job.internal.JobState;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.local.QuartzCronSupport;
import cn.hg.aifei.job.support.EventProbe;
import cn.hg.aifei.job.support.TestJobs;
import cn.hg.aifei.job.support.TestLogs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 执行层提交被拒语义（设计决策 7 / §6 契约表「执行池队列满」行 / A08、A19）：{@code pool.size = 1} + {@code pool.queue = 1}
 * + 一个阻塞任务占满唯一工作线程与队列后，第三个任务的提交必然被拒。
 *
 * <p><b>拒绝信号如何被断言</b>：执行层固定 {@code AbortPolicy}，{@code RejectedExecutionException} 只能在
 * {@code JobWakeTask#run} 的 {@code catch} 里被消费——想直接捕获它，必须反射替换执行层的拒绝策略或包装执行层，
 * 那会<b>改变被测行为</b>。故本类用四条<b>实现侧无法绕开</b>的间接证据锁定这条路径：</p>
 * <ol>
 *   <li><b>任务体一次都没执行</b>（{@code runs() == 0}）却已有 {@code SKIPPED} 记账——{@code JobRunner} 的
 *       {@code SKIPPED} 必须先跑完任务体才产生，因此该事件只能来自「提交被拒」这一处；</li>
 *   <li>该 {@code SKIPPED} 的<b>回调线程是唤醒层的单一线程</b>：多次拒绝的回调线程同一个实例，且与执行层线程不同
 *       （比对 {@link Thread} 引用，<b>不依赖线程名</b>）；</li>
 *   <li>拒绝后<b>同一任务按节拍继续被拒</b>（{@code SKIPPED} 持续增长）——证明唤醒线程未被阻塞且已就地重排；</li>
 *   <li>{@code SKIPPED} 事件时间不是「提交时刻」而是<b>原计划触发时刻</b>：本类第二个用例用「监听器在首个
 *       {@code SKIPPED} 回调内阻塞唤醒线程 400ms」把这个差值放大到可断言量级。</li>
 * </ol>
 */
public class RejectionRescheduleTest {

    /**
     * 触发间隔，单位毫秒。
     */
    private static final long PERIOD = 20L;

    /**
     * 排队 / 被拒任务「必须出现」的拒绝次数，用于断言唤醒层持续按节拍投递。
     */
    private static final int REJECTIONS = 3;

    /**
     * 监听器阻塞唤醒线程的时长，单位毫秒（把「计划时刻 vs 提交时刻」的差值放大到可断言量级）。
     */
    private static final long WAKE_PAUSE_MILLIS = 400L;

    /**
     * 「事件时间早于当前时刻」的下限，单位毫秒。
     */
    private static final long STALE_THRESHOLD_MILLIS = 150L;

    /**
     * 计划时刻与 {@code start} 采样时刻的允许偏差，单位毫秒。
     */
    private static final long PLANNED_TIME_TOLERANCE_MILLIS = 50L;

    /**
     * 本类装配过的任务体。
     */
    private final List<TestJobs.Task> bodies = new ArrayList<>();

    /**
     * 未捕获异常收集器。
     */
    private final List<Throwable> uncaught = Collections.synchronizedList(new ArrayList<Throwable>());

    /**
     * 调度器。
     */
    private JobScheduler scheduler;

    /**
     * 状态记账。
     */
    private JobState state;

    /**
     * 事件探针。
     */
    private EventProbe probe;

    /**
     * 用例前的默认未捕获异常处理器。
     */
    private Thread.UncaughtExceptionHandler previousHandler;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    /**
     * 安装未捕获异常收集器。
     */
    @Before
    public void setUp() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                uncaught.add(error);
            }
        });
        probe = new EventProbe();
        state = new JobState();
    }

    /**
     * 释放任务体、停机、还原处理器，并断言无未捕获异常。
     */
    @After
    public void tearDown() {
        for (TestJobs.Task body : bodies) {
            body.unblock();
        }
        if (scheduler != null) {
            scheduler.stop();
        }
        JobHolder.clear();
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        assertTrue("不得有未捕获异常，实际收到：" + uncaught, uncaught.isEmpty());
    }

    // ------------------------------------------------------------------ 拒绝 + 记账 + 唤醒线程不阻塞 + 就地重排

    /**
     * 队列占满：被拒任务<b>一次都没执行</b>却记了 {@code SKIPPED}；唤醒线程未被阻塞（拒绝持续按节拍发生）；
     * 队列腾空后该任务<b>下一周期仍被触发</b>（证明显式就地重排，未永久停摆）。
     */
    @Test
    public void rejectionIsBookedAndTaskKeepsFiringAfterQueueDrains() {
        TestJobs.Task blocker = new TestJobs.Task().block();
        TestJobs.Task filler = new TestJobs.Task();
        TestJobs.Task victim = new TestJobs.Task();
        bodies.add(blocker);
        bodies.add(filler);
        bodies.add(victim);
        // 任务名按方法名排序：alpha（占住唯一工作线程）→ beta（占满容量 1 的队列）→ gamma（提交必被拒）
        JobTask blockerTask = TestJobs.fixedRate(blocker, "alpha", PERIOD, 0L);
        JobTask fillerTask = TestJobs.fixedRate(filler, "beta", PERIOD, 0L);
        JobTask victimTask = TestJobs.fixedRate(victim, "gamma", PERIOD, 0L);
        start(blockerTask, fillerTask, victimTask);

        assertTrue("被拒任务应记 SKIPPED（证明提交被拒且已记账）",
                probe.awaitPhase(victimTask.name(), JobEvent.Phase.SKIPPED, 1, TestJobs.TIMEOUT_MILLIS));

        assertEquals("被拒任务的任务体不得执行（SKIPPED 只能来自提交被拒）", 0, victim.runs());
        assertEquals("队列已被占满，占位任务不得执行", 0, filler.runs());
        assertFalse("阻塞任务仍在执行中", probe.count(blockerTask.name(), JobEvent.Phase.SUCCESS) > 0);
        assertTrue("被拒任务不得出现任何执行类事件：" + probe.phasesOf(victimTask.name()),
                probe.count(victimTask.name(), JobEvent.Phase.STARTED) == 0);

        assertTrue("拒绝后唤醒层应继续按节拍投递（就地重排），至少 " + REJECTIONS + " 次",
                probe.awaitPhase(victimTask.name(), JobEvent.Phase.SKIPPED, REJECTIONS, TestJobs.TIMEOUT_MILLIS));

        assertTrue("阻塞任务应已真正进入任务体（用于取出执行层线程作对照）",
                blocker.awaitFirstEntry(TestJobs.TIMEOUT_MILLIS));
        List<JobEvent> skipped = phaseEvents(victimTask.name(), JobEvent.Phase.SKIPPED);
        Thread wakeThread = callbackThreadOf(victimTask.name(), skipped.get(skipped.size() - 1));
        assertNotSame("拒绝记账必须发生在唤醒层，而不是执行任务体的执行层线程", blocker.bodyThread(), wakeThread);
        for (JobEvent event : skipped) {
            assertSame("同一任务的全部拒绝事件应来自唤醒层同一线程（全局仅 1 个唤醒线程）" + event, wakeThread,
                    callbackThreadOf(victimTask.name(), event));
        }

        // ④ 队列腾空后该任务下一周期仍被触发：若被拒后没有就地重排，该任务将永久停摆。
        blocker.unblock();
        assertTrue("队列腾空后被拒任务应恢复触发（就地重排生效）",
                probe.awaitPhase(victimTask.name(), JobEvent.Phase.SUCCESS, 1, TestJobs.TIMEOUT_MILLIS));
        assertTrue("被拒任务最终应真正执行过", victim.runs() >= 1);
    }

    // ------------------------------------------------------------------ 事件时间口径

    /**
     * {@code SKIPPED} 的事件时间是<b>原计划触发时刻</b>，不是「提交 / 记账时刻」。
     *
     * <p>放大手法：监听器在首个 {@code SKIPPED} 回调内阻塞唤醒线程 {@link #WAKE_PAUSE_MILLIS}ms（同步回调，
     * 见设计决策 14），于是「暂停之后才轮到投递的那个任务」的提交被推迟到计划时刻之后约 400ms 才发生。
     * 若实现用当前时刻当事件时间，差值为 0；取原计划触发时刻才有本用例断言的差值。</p>
     *
     * <p><b>任务顺序由任务名（方法名）排序决定</b>：{@code alpha → beta → delta → gamma}。故先被拒的是
     * {@code delta}（监听器就在它的 {@code SKIPPED} 回调里暂停唤醒线程），暂停结束后才轮到 {@code gamma} 被拒——
     * 断言对象必须是 {@code gamma}，否则测的是「暂停之前」的事件、差值恒为 0。</p>
     */
    @Test
    public void skippedEventTimeIsPlannedTimeNotSubmissionTime() {
        PausingProbe listener = new PausingProbe(probe, WAKE_PAUSE_MILLIS);
        TestJobs.Task blocker = new TestJobs.Task().block();
        TestJobs.Task filler = new TestJobs.Task();
        TestJobs.Task pausedVictim = new TestJobs.Task();
        TestJobs.Task lateVictim = new TestJobs.Task();
        bodies.add(blocker);
        bodies.add(filler);
        bodies.add(pausedVictim);
        bodies.add(lateVictim);
        JobTask blockerTask = TestJobs.fixedRate(blocker, "alpha", PERIOD, 0L);
        JobTask fillerTask = TestJobs.fixedRate(filler, "beta", PERIOD, 0L);
        JobTask pausedVictimTask = TestJobs.fixedRate(pausedVictim, "delta", PERIOD, 0L);
        JobTask lateVictimTask = TestJobs.fixedRate(lateVictim, "gamma", PERIOD, 0L);

        long startMillis = System.currentTimeMillis();
        start(listener, blockerTask, fillerTask, pausedVictimTask, lateVictimTask);

        assertTrue("暂停之后被投递的任务应记 SKIPPED",
                probe.awaitPhase(lateVictimTask.name(), JobEvent.Phase.SKIPPED, 1, TestJobs.TIMEOUT_MILLIS));
        long observedAt = System.currentTimeMillis();

        assertEquals("暂停之后被投递的任务的任务体不得执行", 0, lateVictim.runs());
        assertEquals("先被拒的任务（触发暂停者）的任务体也不得执行", 0, pausedVictim.runs());
        List<JobEvent> skipped = phaseEvents(lateVictimTask.name(), JobEvent.Phase.SKIPPED);
        JobEvent event = skipped.get(0);
        assertTrue("事件时间应是原计划触发时刻（≈ start 时刻 " + startMillis + "），实际 " + event.triggerTime(),
                Math.abs(event.triggerTime() - startMillis) <= PLANNED_TIME_TOLERANCE_MILLIS);
        assertTrue("事件时间必须明显早于提交 / 记账时刻（唤醒线程被阻塞了 " + WAKE_PAUSE_MILLIS + "ms），实际差值 "
                        + (observedAt - event.triggerTime()),
                observedAt - event.triggerTime() >= STALE_THRESHOLD_MILLIS);
    }

    // ------------------------------------------------------------------ 装配

    /**
     * 手工装配 {@code pool.size = 1} + {@code pool.queue = 1} 的调度内核并启动。
     *
     * @param tasks 任务定义，按任务名排序后依次投递
     */
    private void start(JobTask... tasks) {
        start(probe, tasks);
    }

    /**
     * 手工装配并启动，监听器可替换（用于在回调内阻塞唤醒线程）。
     *
     * @param listener 监听器
     * @param tasks 任务定义
     */
    private void start(JobListener listener, JobTask... tasks) {
        JobRegistry registry = TestJobs.registry(tasks);
        JobHolder.install(registry, state);
        scheduler = new JobScheduler(TestJobs.config(1, 1, 500L), registry, state, new JobEvents(listener),
                new JobSchedule(new QuartzCronSupport()));
        scheduler.start();
    }

    /**
     * @param taskName 任务名
     * @param phase 阶段
     * @return 该任务该阶段的事件，按到达顺序
     */
    private List<JobEvent> phaseEvents(String taskName, JobEvent.Phase phase) {
        List<JobEvent> matched = new ArrayList<>();
        for (JobEvent event : probe.eventsOf(taskName)) {
            if (event.phase() == phase) {
                matched.add(event);
            }
        }
        return matched;
    }

    /**
     * 取与该事件对象同一实例的回调线程：{@link EventProbe} 的事件列表与线程列表索引一一对应。
     *
     * @param taskName 任务名
     * @param target 目标事件
     * @return 该事件的回调线程
     */
    private Thread callbackThreadOf(String taskName, JobEvent target) {
        List<JobEvent> all = probe.events();
        List<Thread> threads = probe.callbackThreads();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i) == target) {
                return threads.get(i);
            }
        }
        throw new AssertionError("事件不在探针记录中：" + target + "（" + taskName + "）");
    }

    /**
     * 在首个 {@code SKIPPED} 回调内<b>阻塞唤醒线程</b>的监听器：先把事件交给探针记账，再一次性阻塞。
     *
     * <p>它只用于把「计划时刻与提交时刻的差值」放大；监听器同步回调于产生事件的线程（此处即唤醒线程）。</p>
     */
    private static final class PausingProbe implements JobListener {

        /**
         * 真实探针（记录全部事件与回调线程）。
         */
        private final EventProbe probe;

        /**
         * 阻塞时长，单位毫秒。
         */
        private final long pauseMillis;

        /**
         * 保证只阻塞一次。
         */
        private final AtomicBoolean paused = new AtomicBoolean();

        /**
         * @param probe 真实探针
         * @param pauseMillis 阻塞时长，单位毫秒
         */
        PausingProbe(EventProbe probe, long pauseMillis) {
            this.probe = probe;
            this.pauseMillis = pauseMillis;
        }

        @Override
        public void onEvent(JobEvent event) {
            probe.onEvent(event);
            if (event.phase() == JobEvent.Phase.SKIPPED && paused.compareAndSet(false, true)) {
                try {
                    new CountDownLatch(1).await(pauseMillis, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
