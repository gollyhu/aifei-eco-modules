/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
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
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * {@code initialDelay} 语义（设计决策 7 的「{@code initialDelay} 只在首次分支读取一次」/ A06、A19）：
 * 只影响首次触发；第二次起的间隔由 {@code period} 决定；<b>连续被拒不累加</b> {@code initialDelay}。
 *
 * <p>两处断言都<b>不断言精确毫秒</b>：①「首次不早于 {@code initialDelay}」用宽松下界（{@code initialDelay} − 容差）；
 * ②「只影响首次」用「第一次与第二次之间的间隔远小于 {@code initialDelay}」这一<b>量级差</b>表达；
 * ③「连续被拒不累加」用「{@code initialDelay} 取大值 + 队列持续占满时，要求 N 次拒绝在 5s 内到齐」表达——
 * 若每次重排都重新消费 {@code initialDelay}，所需时间会线性超出 5s 上限。</p>
 */
public class InitialDelayTest {

    /**
     * 触发间隔，单位毫秒。
     */
    private static final long PERIOD = 20L;

    /**
     * 首次延迟，单位毫秒。
     */
    private static final long INITIAL_DELAY = 200L;

    /**
     * 「首次不早于 initialDelay」的允许偏差，单位毫秒（只用于吸收时间采样与线程调度开销）。
     */
    private static final long INITIAL_DELAY_TOLERANCE_MILLIS = 100L;

    /**
     * 大值首次延迟，单位毫秒：若被拒重排重复消费它，N 次拒绝将远超 5s 超时。
     */
    private static final long LARGE_INITIAL_DELAY = 1500L;

    /**
     * 拒绝路径要求观测到的拒绝次数。
     */
    private static final int REQUIRED_REJECTIONS = 5;

    /**
     * 重排间隔的宽松上限，单位毫秒：介于 {@link #PERIOD} 与 {@link #LARGE_INITIAL_DELAY} 之间。
     */
    private static final long RESCHEDULE_GAP_MAX_MILLIS = 500L;

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

    // ------------------------------------------------------------------ 只影响首次

    /**
     * 首次触发不早于 {@code initialDelay}，第二次起的间隔≈{@code period}（证明只影响首次）。
     */
    @Test
    public void initialDelayDelaysOnlyTheFirstFire() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, INITIAL_DELAY);
        long startMillis = System.currentTimeMillis();
        start(2, 4, 500L, task);

        assertTrue("应触发至少三次", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 3,
                TestJobs.TIMEOUT_MILLIS));
        // STARTED 早于任务体：读取开始时刻数组前必须等三次都真的进入任务体，否则是竞态。
        assertTrue("三次执行应都已进入任务体", body.awaitEntries(3, TestJobs.TIMEOUT_MILLIS));

        long firstDelay = body.startTime(0) - startMillis;
        assertTrue("首次触发不得早于 initialDelay 减去容差，实际首次延迟 " + firstDelay,
                firstDelay >= INITIAL_DELAY - INITIAL_DELAY_TOLERANCE_MILLIS);

        long firstGap = body.startTime(1) - body.startTime(0);
        long secondGap = body.startTime(2) - body.startTime(1);
        assertTrue("第二次间隔应远小于 initialDelay（若被重复消费则必然超限），实际 " + firstGap,
                firstGap < INITIAL_DELAY / 2);
        assertTrue("第三次间隔应远小于 initialDelay（若被重复消费则必然超限），实际 " + secondGap,
                secondGap < INITIAL_DELAY / 2);
        assertTrue("间隔不得为 0（同一任务不重叠），实际 " + firstGap + " / " + secondGap,
                firstGap >= 5L && secondGap >= 5L);
    }

    // ------------------------------------------------------------------ 连续被拒不累加

    /**
     * 连续被拒不累加 {@code initialDelay}：{@code initialDelay} 取大值（1.5s）而 {@code period} 为 20ms，
     * 队列持续占满时要求 5 次拒绝在 5s 宽松超时内到齐，且相邻拒绝的间隔远小于 {@code initialDelay}。
     *
     * <p>依据：{@code JobWakeTask} 的 {@code catch} 内重排时 {@code isFirst} 写死 {@code false}，
     * 因而 {@code nextDelay} 只按 {@code period} 前进；若重复消费 {@code initialDelay}，5 次拒绝需约
     * {@code 1.5s × 4 = 6s}，必然超时。</p>
     */
    @Test
    public void rejectionDoesNotAccumulateInitialDelay() {
        TestJobs.Task blocker = new TestJobs.Task().block();
        TestJobs.Task filler = new TestJobs.Task();
        TestJobs.Task victim = new TestJobs.Task();
        bodies.add(blocker);
        bodies.add(filler);
        bodies.add(victim);
        // 任务名按方法名排序：alpha 占住唯一工作线程 → beta 占满队列 → gamma 的每次提交必被拒
        JobTask blockerTask = TestJobs.fixedRate(blocker, "alpha", PERIOD, 0L);
        JobTask fillerTask = TestJobs.fixedRate(filler, "beta", PERIOD, 0L);
        JobTask victimTask = TestJobs.fixedRate(victim, "gamma", PERIOD, LARGE_INITIAL_DELAY);
        start(1, 1, 500L, blockerTask, fillerTask, victimTask);

        assertTrue("首次延迟 " + LARGE_INITIAL_DELAY + "ms 后应开始被拒，且连续 " + REQUIRED_REJECTIONS
                        + " 次拒绝须在 5s 内到齐（若重复消费 initialDelay 会超时）",
                probe.awaitPhase(victimTask.name(), JobEvent.Phase.SKIPPED, REQUIRED_REJECTIONS,
                        TestJobs.TIMEOUT_MILLIS));
        assertEquals("被拒任务的任务体不得执行", 0, victim.runs());

        List<JobEvent> skipped = phaseEvents(victimTask.name(), JobEvent.Phase.SKIPPED);
        for (int i = 1; i < skipped.size(); i++) {
            long gap = skipped.get(i).triggerTime() - skipped.get(i - 1).triggerTime();
            assertTrue("被拒重排应按 period 前进（间隔远小于 initialDelay），第 " + i + " 次间隔 " + gap,
                    gap < RESCHEDULE_GAP_MAX_MILLIS);
        }
    }

    // ------------------------------------------------------------------ 装配

    /**
     * 手工装配调度内核并启动。
     *
     * @param poolSize 执行层线程数
     * @param poolQueue 执行层队列容量
     * @param shutdownTimeoutMillis 停机等待上限，单位毫秒
     * @param tasks 任务定义
     */
    private void start(int poolSize, int poolQueue, long shutdownTimeoutMillis, JobTask... tasks) {
        JobRegistry registry = TestJobs.registry(tasks);
        JobHolder.install(registry, state);
        scheduler = new JobScheduler(TestJobs.config(poolSize, poolQueue, shutdownTimeoutMillis), registry, state,
                new JobEvents(probe), new JobSchedule(new QuartzCronSupport()));
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
}
