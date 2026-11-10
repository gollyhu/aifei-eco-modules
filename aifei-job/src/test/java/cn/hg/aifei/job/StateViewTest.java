/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 观测口径（设计 §6「只读观测面补充」/ A15、A18）：{@code state()} 是「最后已知结果」。
 *
 * <ul>
 *   <li>首次执行中（{@code STARTED} 已发、终态未发）该任务<b>不出现</b>在 {@code state()} 中；</li>
 *   <li>已有终态的任务再次执行中<b>仍返回上一次终态</b>（不会暂时消失）；</li>
 *   <li>执行结束后终态<b>覆盖</b>旧值（{@code SUCCESS} 被后续 {@code FAILED} 覆盖）。</li>
 * </ul>
 *
 * <p>执行中用 {@link TestJobs.Task#block()} 把任务体挂在 {@code Semaphore} 上，使「执行中」窗口开到用例断言足够宽，
 * 无需 sleep；每轮由 {@code release(1)} 放行一次。</p>
 */
public class StateViewTest {

    /**
     * 触发间隔，单位毫秒。
     */
    private static final long PERIOD = 20L;

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
     * 释放任务体、停机、清观测面、还原处理器，并断言无未捕获异常。
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

    // ------------------------------------------------------------------ 首次执行中

    /**
     * 首次执行中（尚无任何终态）该任务<b>不出现</b>在 {@code state()} 中，但 {@code names()} 可见。
     */
    @Test
    public void runningTaskWithoutTerminalIsAbsent() {
        TestJobs.Task body = new TestJobs.Task().block();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(task);

        assertTrue("应已进入首次执行", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertFalse("首次执行中不得出现在 state() 中", JobKit.state().containsKey(task.name()));
        assertTrue("names() 应区分「已注册未执行」与「未注册」", JobKit.names().contains(task.name()));

        body.release(1);
        assertTrue("应跑完首次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertEquals("终态应进入 state()", JobEvent.Phase.SUCCESS, terminal(task));
    }

    // ------------------------------------------------------------------ 再次执行中保留上一次终态

    /**
     * 已有终态的任务再次执行中<b>仍返回上一次终态</b>（{@code STARTED} 不记账、不覆盖）。
     */
    @Test
    public void runningTaskKeepsPreviousTerminal() {
        TestJobs.Task body = new TestJobs.Task().block();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(task);

        assertTrue("应进入首次执行", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));
        body.release(1);
        assertTrue("应完成首次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        long firstTerminalTime = JobKit.state().get(task.name()).triggerTime();

        assertTrue("应进入第二次执行", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2,
                TestJobs.TIMEOUT_MILLIS));
        Map<String, JobEvent> snapshot = JobKit.state();
        assertTrue("执行中的任务不得从 state() 消失", snapshot.containsKey(task.name()));
        assertEquals("执行中应保留上一次终态", JobEvent.Phase.SUCCESS, snapshot.get(task.name()).phase());
        assertEquals("上一次终态的时间戳不得被 STARTED 改写", firstTerminalTime,
                snapshot.get(task.name()).triggerTime());

        body.release(1);
        assertTrue("应完成第二次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 2,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("结束后终态应被新一轮覆盖", JobKit.state().get(task.name()).triggerTime() > firstTerminalTime);
    }

    // ------------------------------------------------------------------ 终态覆盖

    /**
     * 执行结束后终态覆盖：{@code SUCCESS} 被后续 {@code FAILED} 覆盖，且 {@code FAILED} 带异常原因。
     */
    @Test
    public void terminalStateIsOverwrittenByNextRun() {
        IllegalStateException boom = new IllegalStateException("third run fails");
        TestJobs.Task body = new TestJobs.Task().block().failOnRun(3, boom);
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(task);

        assertTrue("应进入首轮执行", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));
        body.release(1);
        assertTrue("首轮应成功", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertEquals(JobEvent.Phase.SUCCESS, terminal(task));

        assertTrue("应进入第二轮执行", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2,
                TestJobs.TIMEOUT_MILLIS));
        assertEquals("第二轮执行中应保留上一轮 SUCCESS", JobEvent.Phase.SUCCESS, terminal(task));
        body.release(1);
        assertTrue("第二轮应成功", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 2,
                TestJobs.TIMEOUT_MILLIS));
        assertEquals(JobEvent.Phase.SUCCESS, terminal(task));

        assertTrue("应进入第三轮执行", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 3,
                TestJobs.TIMEOUT_MILLIS));
        body.release(1);
        assertTrue("第三轮应失败", probe.awaitPhase(task.name(), JobEvent.Phase.FAILED, 1,
                TestJobs.TIMEOUT_MILLIS));
        Map<String, JobEvent> snapshot = JobKit.state();
        assertEquals("终态应被本轮 FAILED 覆盖", JobEvent.Phase.FAILED, snapshot.get(task.name()).phase());
        assertNotNull("FAILED 终态应携带异常原因", snapshot.get(task.name()).error());
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

    /**
     * @param task 任务定义
     * @return 该任务在 {@code state()} 中的终态；不存在时断言失败
     */
    private JobEvent.Phase terminal(JobTask task) {
        JobEvent event = JobKit.state().get(task.name());
        assertNotNull("state() 中应有该任务的终态", event);
        return event.phase();
    }
}
