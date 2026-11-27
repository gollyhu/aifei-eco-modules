/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * 优雅停机（设计决策 9 / 决策 8 第 2 条 / A09）：{@code stop()} 后不再有新触发；在途任务的续排在唤醒层已
 * {@code shutdown()} 时被拒，<b>静默终止、不外抛、不留未捕获堆栈</b>；{@code stop()} 幂等；停机后观测面为空集合。
 *
 * <p>「不外抛」用 {@link Thread#setDefaultUncaughtExceptionHandler} 收集器断言为空（执行层线程若带未处理异常结束，
 * 异常会经它送达）；「不再有新触发」用<b>稳定等待 200ms 后计数不变</b>表达，不断言精确时序。</p>
 */
public class ShutdownTest {

    /**
     * 触发间隔，单位毫秒。
     */
    private static final long PERIOD = 20L;

    /**
     * 停机等待上限（正常路径：足够覆盖一次毫秒级任务），单位毫秒。
     */
    private static final long GRACEFUL_TIMEOUT_MILLIS = 500L;

    /**
     * 停机等待上限（超时路径：短到必然对阻塞任务超时），单位毫秒。
     */
    private static final long FORCED_TIMEOUT_MILLIS = 50L;

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
     * 释放任务体、停机（幂等）、清观测面、还原处理器，并断言无未捕获异常。
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
        assertTrue("停机不得外抛（含续排被拒的 RejectedExecutionException），实际收到：" + uncaught,
                uncaught.isEmpty());
    }

    // ------------------------------------------------------------------ 停止新触发

    /**
     * {@code stop()} 后不再有新触发：稳定等待 200ms 后 {@code STARTED} / {@code SUCCESS} 计数均不变。
     */
    @Test
    public void stopStopsNewTriggers() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(GRACEFUL_TIMEOUT_MILLIS, task);

        assertTrue("停机前应已正常完成两次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 2,
                TestJobs.TIMEOUT_MILLIS));

        scheduler.stop();

        // 基准在 stop() 返回后采：停机过程中已开始的那一轮计入基准，断言只针对「stop() 返回后是否有新触发」。
        int startedBefore = probe.count(task.name(), JobEvent.Phase.STARTED);
        int runsBefore = body.runs();

        // 稳定等待：只用于「确认不再有新变化」，不用于时序断言。
        TestJobs.stableWait(4 * TestJobs.STABLE_WAIT_MILLIS);
        assertEquals("stop() 后不得再有新触发（period 仅 " + PERIOD + "ms，等待期间本应触发多次）", startedBefore,
                probe.count(task.name(), JobEvent.Phase.STARTED));
        assertEquals("stop() 后任务体不得再被执行", runsBefore, body.runs());
    }

    // ------------------------------------------------------------------ 在途任务续排被拒

    /**
     * 超时停机路径：在途阻塞任务被中断，其 {@code finally} 里的续排因唤醒层已 {@code shutdown()} 被拒——
     * 必须静默终止（{@code stop()} 不抛、无未捕获堆栈）。
     */
    @Test
    public void inFlightRescheduleRejectionIsSilent() {
        TestJobs.Task body = new TestJobs.Task().block();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(FORCED_TIMEOUT_MILLIS, task);

        assertTrue("应有在途任务", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));
        // STARTED 早于任务体，故必须等「任务体已进入」信号，不能拿 runs() 当在途依据（否则是竞态）。
        assertTrue("在途任务应已真正进入任务体", body.awaitFirstEntry(TestJobs.TIMEOUT_MILLIS));

        scheduler.stop();

        assertTrue("停机超时应中断在途任务（任务须自行响应中断）",
                body.awaitInterrupted(TestJobs.TIMEOUT_MILLIS));
        assertTrue("续排被拒不得外抛，实际收到：" + uncaught, uncaught.isEmpty());
        assertEquals("任务体不得因停机异常而误记 FAILED 之外的多余终态", 0,
                probe.count(task.name(), JobEvent.Phase.FAILED));
    }

    // ------------------------------------------------------------------ 幂等与观测面

    /**
     * {@code stop()} 幂等：连续调用不抛、不重复记账、不改变事件总数。
     */
    @Test
    public void stopIsIdempotent() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(GRACEFUL_TIMEOUT_MILLIS, task);

        assertTrue("停机前应已触发", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));
        scheduler.stop();
        int eventsAfterFirstStop = probe.size();
        int runsAfterFirstStop = body.runs();

        scheduler.stop();

        assertEquals("重复 stop() 不得新增事件", eventsAfterFirstStop, probe.size());
        assertEquals("重复 stop() 不得再执行任务体", runsAfterFirstStop, body.runs());
    }

    /**
     * 停机后观测面返回空集合：{@code JobKit.names()} / {@code state()} 皆空且不抛（A13）。
     */
    @Test
    public void observationIsEmptyAfterStop() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(GRACEFUL_TIMEOUT_MILLIS, task);

        assertTrue("停机前应产生终态", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("停机前 names() 应可见任务", JobKit.names().contains(task.name()));
        assertFalse("停机前 state() 应有终态", JobKit.state().isEmpty());

        scheduler.stop();

        assertTrue("停机后 names() 应为空集合", JobKit.names().isEmpty());
        assertTrue("停机后 state() 应为空集合", JobKit.state().isEmpty());
    }

    // ------------------------------------------------------------------ 装配

    /**
     * 手工装配单任务调度内核并启动。
     *
     * @param shutdownTimeoutMillis 停机等待上限，单位毫秒
     * @param task 任务定义
     */
    private void start(long shutdownTimeoutMillis, JobTask task) {
        JobRegistry registry = TestJobs.registry(task);
        JobHolder.install(registry, state);
        scheduler = new JobScheduler(TestJobs.config(2, 4, shutdownTimeoutMillis), registry, state,
                new JobEvents(probe), new JobSchedule(new QuartzCronSupport()));
        scheduler.start();
    }
}
