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
 * 固定频率超时跳过语义（事件时间口径）：
 * 任务耗时超过 {@code period} 时记一条 {@code SKIPPED}，其<b>事件时间 = {@code lastStart + period}</b>（原计划触发时刻），
 * 再以当前时刻为新基准立即重排，<b>已过触发点不补跑</b>。
 *
 * <p>「耗时超过 period」由任务体自身的 {@link TestJobs.Task#slow(long)} 造成（400ms 对 20ms 的 period，量级差 20 倍），
 * 用例侧不 sleep、不断言「第 N 次触发在 X 毫秒内」；「事件时间不是当前时刻」用「{@code SKIPPED} 事件时间
 * ≤ {@code SUCCESS} 事件时间 − 2 个 period」表达，与实现无关且留足余量。</p>
 */
public class FixedRateSkipTest {

    /**
     * 触发间隔，单位毫秒。
     */
    private static final long PERIOD = 20L;

    /**
     * 任务体耗时，单位毫秒（远大于 {@link #PERIOD}，使每次执行都必然越过下一个计划点）。
     */
    private static final long BODY_MILLIS = 400L;

    /**
     * 事件时间与「任务体开始时刻 + period」的允许偏差，单位毫秒（吸收 {@code currentTimeMillis} 采样先后与调度开销）。
     */
    private static final long TIME_TOLERANCE_MILLIS = 10L;

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

    // ------------------------------------------------------------------ 事件时间口径

    /**
     * {@code SKIPPED} 已记账，且事件时间 = 原计划触发时刻（{@code lastStart + period}），<b>不等于</b>当前时刻。
     *
     * <p>任务体耗时 400ms、{@code period} 20ms：本次执行结束时的「当前时刻」与计划点相差约 380ms，
     * 因此「事件时间 ≤ 终态事件时间 − 2 个 period」只有取原计划触发时刻才能成立。</p>
     */
    @Test
    public void overdueRunRecordsSkippedAtPlannedTime() {
        TestJobs.Task body = new TestJobs.Task().slow(BODY_MILLIS);
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(task);

        assertTrue("应成功完成一次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("耗时为 period 的 20 倍，必须记一条 SKIPPED",
                probe.awaitPhase(task.name(), JobEvent.Phase.SKIPPED, 1, TestJobs.TIMEOUT_MILLIS));

        JobEvent skipped = probe.eventsOf(task.name()).get(2);
        JobEvent success = probe.eventsOf(task.name()).get(1);
        assertEquals("第 3 个事件应为 SKIPPED（STARTED → SUCCESS → SKIPPED）", JobEvent.Phase.SKIPPED,
                skipped.phase());
        assertEquals("SKIPPED 不携带异常", null, skipped.error());

        long expected = body.startTime(0) + PERIOD;
        assertTrue("事件时间应等于原计划触发时刻（lastStart + period = " + expected + "），实际 "
                        + skipped.triggerTime(),
                Math.abs(skipped.triggerTime() - expected) <= TIME_TOLERANCE_MILLIS);
        assertTrue("事件时间必须早于终态事件时间至少 2 个 period（证明不是当前时刻），实际差值 "
                        + (success.triggerTime() - skipped.triggerTime()),
                skipped.triggerTime() <= success.triggerTime() - 2 * PERIOD);
    }

    // ------------------------------------------------------------------ 重排与不补跑

    /**
     * 以当前时刻为新基准立即重排，且<b>已过触发点不补跑</b>：
     * 第二次执行与第一次之间隔着任务体耗时（而非 0ms 连续执行），且每轮至多一条 {@code SKIPPED}。
     */
    @Test
    public void overdueRunReschedulesFromNowWithoutReplay() {
        TestJobs.Task body = new TestJobs.Task().slow(BODY_MILLIS);
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(task);

        assertTrue("应完成第一次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("应记第一次 SKIPPED", probe.awaitPhase(task.name(), JobEvent.Phase.SKIPPED, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("应立即重排并触发第二次", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2,
                TestJobs.TIMEOUT_MILLIS));

        JobEvent firstSuccess = probe.eventsOf(task.name()).get(1);
        JobEvent secondStarted = probe.eventsOf(task.name()).get(3);
        assertEquals("第 4 个事件应为第二次 STARTED", JobEvent.Phase.STARTED, secondStarted.phase());
        assertTrue("重排基准是「当前时刻」：第二次触发不早于第一次的终态，实际差 "
                        + (secondStarted.triggerTime() - firstSuccess.triggerTime()),
                secondStarted.triggerTime() >= firstSuccess.triggerTime());

        // STARTED 早于任务体：读取第二次的开始时刻前必须等它真的进入任务体，否则是竞态。
        assertTrue("第二次应已进入任务体", body.awaitEntries(2, TestJobs.TIMEOUT_MILLIS));
        long gap = body.startTime(1) - body.startTime(0);
        assertTrue("不补跑：两次执行之间隔着任务体耗时（≥ 5 个 period），实际间隔 " + gap,
                gap >= 5 * PERIOD);

        assertTrue("第二轮也应记一条 SKIPPED", probe.awaitPhase(task.name(), JobEvent.Phase.SKIPPED, 2,
                TestJobs.TIMEOUT_MILLIS));
        List<JobEvent> skipped = phaseEvents(task.name(), JobEvent.Phase.SKIPPED);
        long skippedGap = skipped.get(1).triggerTime() - skipped.get(0).triggerTime();
        // 不补跑的关键证据：已过的计划点被「折叠」成一条 SKIPPED，于是相邻两个 SKIPPED 的事件时间相隔任务体耗时；
        // 若实现逐个重放已过计划点，相邻 SKIPPED 只会相隔 1 个 period。
        assertTrue("相邻 SKIPPED 事件时间应相隔任务体耗时（≥ 5 个 period），实际 " + skippedGap,
                skippedGap >= 5 * PERIOD);
    }

    // ------------------------------------------------------------------ 事件筛选

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

    // ------------------------------------------------------------------ 装配

    /**
     * 手工装配单任务调度内核并启动。
     *
     * @param task 任务定义
     */
    private void start(JobTask task) {
        JobRegistry registry = TestJobs.registry(task);
        JobHolder.install(registry, state);
        scheduler = new JobScheduler(TestJobs.config(2, 4, 500L), registry, state, new JobEvents(probe),
                new JobSchedule(new QuartzCronSupport()));
        scheduler.start();
    }
}
