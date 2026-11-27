/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 监听器契约（线程归属）：{@code STARTED} 先于任务体开始、同任务事件顺序
 * {@code STARTED → 终态}、终态<b>先记账再回调</b>、{@code STARTED} 回调内看到的是<b>上一次终态</b>、
 * 回调异常不影响 {@code state()} 与后续触发、回调跑在产生事件的线程上。
 *
 * <p>监听器实现 {@link HookedListener} 把全部记录委托给 {@link EventProbe}（复用替身），只额外提供「在回调内阻塞」
 * 「在回调内读 {@code JobKit.state()}」「在回调内抛异常」三个钩子——这是<b>行为注入</b>，不是另造一份探针。</p>
 */
public class ListenerContractTest {

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
        assertTrue("监听器异常必须被隔离，不得外抛，实际收到：" + uncaught, uncaught.isEmpty());
    }

    // ------------------------------------------------------------------ STARTED 先于任务体

    /**
     * {@code STARTED} 先于任务体开始：监听器在 {@code STARTED} 回调内阻塞时，任务体<b>尚未执行</b>。
     */
    @Test
    public void startedCallbackRunsBeforeTaskBody() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        HookedListener listener = new HookedListener(probe);
        listener.blockOnStarted();
        start(listener, task);

        assertTrue("监听器应已进入 STARTED 回调", listener.awaitStartedCallback(TestJobs.TIMEOUT_MILLIS));
        // 稳定等待：回调正在阻塞，期间任务体若会执行，20ms 量级的调度早已消化。
        TestJobs.stableWait(TestJobs.STABLE_WAIT_MILLIS);
        assertEquals("STARTED 回调返回前任务体不得开始执行", 0, body.runs());

        listener.releaseStartedCallback();
        assertTrue("释放后应跑完任务体", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertEquals("任务体应执行一次", 1, body.runs());
    }

    // ------------------------------------------------------------------ 顺序

    /**
     * 同一任务的事件顺序恒为 {@code STARTED → 终态}，连续多轮亦然。
     */
    @Test
    public void eventsArriveInStartedThenTerminalOrder() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(new HookedListener(probe), task);

        assertTrue("应至少完成两轮", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 2,
                TestJobs.TIMEOUT_MILLIS));
        List<JobEvent.Phase> phases = probe.phasesOf(task.name());
        assertEquals("事件顺序应为 STARTED → 终态 反复", JobEvent.Phase.STARTED, phases.get(0));
        assertEquals(JobEvent.Phase.SUCCESS, phases.get(1));
        assertEquals(JobEvent.Phase.STARTED, phases.get(2));
        assertEquals(JobEvent.Phase.SUCCESS, phases.get(3));
    }

    // ------------------------------------------------------------------ 先记账再回调

    /**
     * 终态事件<b>先记账再回调</b>：终态回调内调用 {@code JobKit.state()} 已是本次终态（A18）。
     */
    @Test
    public void terminalEventIsRecordedBeforeCallback() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        HookedListener listener = new HookedListener(probe);
        start(listener, task);

        assertTrue("应至少进入一次终态回调", listener.awaitTerminalCallbacks(1, TestJobs.TIMEOUT_MILLIS));
        assertEquals("终态回调内 state() 已是本次终态", JobEvent.Phase.SUCCESS, listener.phaseAtTerminal());
    }

    /**
     * {@code STARTED} 回调内为<b>上一次终态</b>：首次执行时该任务不在 {@code state()} 中，第二次执行时是上一轮的
     * {@code SUCCESS}（{@code STARTED} 本就不记账）。
     */
    @Test
    public void startedCallbackSeesPreviousTerminalState() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        HookedListener listener = new HookedListener(probe);
        start(listener, task);

        assertTrue("应至少进入两次 STARTED 回调", listener.awaitStartedCallbacks(2, TestJobs.TIMEOUT_MILLIS));
        List<JobEvent.Phase> seen = listener.phaseAtStarted();
        assertFalse("首次执行时该任务不应出现在 state() 中", listener.presentAtStarted(0));
        assertNull("首次 STARTED 回调内无上一次终态", seen.get(0));
        assertTrue("第二次执行时 state() 应有上一轮终态", listener.presentAtStarted(1));
        assertEquals("第二次 STARTED 回调内应看到上一轮 SUCCESS", JobEvent.Phase.SUCCESS, seen.get(1));
    }

    // ------------------------------------------------------------------ 异常隔离

    /**
     * 监听器抛异常不影响 {@code state()} 与后续触发：事件照常记账、任务照常续排。
     */
    @Test
    public void listenerExceptionDoesNotAffectStateOrTriggering() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        HookedListener listener = new HookedListener(probe);
        listener.throwOnTerminal();
        start(listener, task);

        assertTrue("终态回调抛异常后仍应继续触发",
                listener.awaitTerminalCallbacks(2, TestJobs.TIMEOUT_MILLIS));
        assertTrue("事件仍应正常送达探针", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2,
                TestJobs.TIMEOUT_MILLIS));
        Map<String, JobEvent> snapshot = JobKit.state();
        assertTrue("回调抛异常不得影响 state() 记账：" + snapshot, snapshot.containsKey(task.name()));
        assertEquals("state() 应为正常终态", JobEvent.Phase.SUCCESS, snapshot.get(task.name()).phase());
        assertTrue("监听器异常不得外抛，实际收到：" + uncaught, uncaught.isEmpty());
    }

    // ------------------------------------------------------------------ 回调线程

    /**
     * 回调同步运行在<b>产生事件的线程</b>上：不是主线程，且同任务 {@code STARTED} 与终态在同一线程。
     */
    @Test
    public void callbackRunsOnProducingThread() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(new HookedListener(probe), task);

        assertTrue("应完成一轮", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        List<Thread> threads = probe.callbackThreads();
        assertNotSame("回调应运行在产生事件的线程，而不是测试主线程", Thread.currentThread(), threads.get(0));
        assertSame("同任务的 STARTED 与终态应在同一执行层线程上串行到达", threads.get(0), threads.get(1));
    }

    // ------------------------------------------------------------------ 装配

    /**
     * 手工装配单任务调度内核并启动。
     *
     * @param listener 监听器
     * @param task 任务定义
     */
    private void start(JobListener listener, JobTask task) {
        JobRegistry registry = TestJobs.registry(task);
        JobHolder.install(registry, state);
        scheduler = new JobScheduler(TestJobs.config(2, 4, 500L), registry, state, new JobEvents(listener),
                new JobSchedule(new QuartzCronSupport()));
        scheduler.start();
    }

    /**
     * 记录委托给 {@link EventProbe}、并额外提供「回调内阻塞 / 读 {@code JobKit.state()} / 抛异常」三个钩子的监听器。
     */
    private static final class HookedListener implements JobListener {

        /**
         * 事件与回调线程的真实记录方。
         */
        private final EventProbe probe;

        /**
         * 保护 {@link #startedCallbacks} / {@link #terminalCallbacks} 并用于通知的监视器。
         */
        private final Object monitor = new Object();

        /**
         * 已执行的 {@code STARTED} 回调数。
         */
        private int startedCallbacks;

        /**
         * 已执行的终态回调数。
         */
        private int terminalCallbacks;

        /**
         * 各次 {@code STARTED} 回调内 {@code state()} 中该任务是否存在。
         */
        private final List<Boolean> presentAtStarted = new ArrayList<>();

        /**
         * 各次 {@code STARTED} 回调内该任务的终态；{@code null} 表示不在 {@code state()} 中。
         */
        private final List<JobEvent.Phase> phaseAtStarted = new ArrayList<>();

        /**
         * 最近一次终态回调内 {@code state()} 的终态。
         */
        private volatile JobEvent.Phase phaseAtTerminal;

        /**
         * 是否在首个 {@code STARTED} 回调内阻塞。
         */
        private volatile boolean blockOnStarted;

        /**
         * 是否在终态回调内抛异常。
         */
        private volatile boolean throwOnTerminal;

        /**
         * 首个 {@code STARTED} 回调已进入阻塞点。
         */
        private final CountDownLatch startedEntered = new CountDownLatch(1);

        /**
         * 释放首个 {@code STARTED} 回调的阻塞。
         */
        private final CountDownLatch startedGate = new CountDownLatch(1);

        /**
         * 是否已阻塞过首个 {@code STARTED} 回调。
         */
        private final AtomicBoolean blockedOnce = new AtomicBoolean();

        /**
         * @param probe 事件探针
         */
        HookedListener(EventProbe probe) {
            this.probe = probe;
        }

        /**
         * 让首个 {@code STARTED} 回调阻塞，直到 {@link #releaseStartedCallback()}。
         *
         * @return 本对象，便于链式装配
         */
        HookedListener blockOnStarted() {
            this.blockOnStarted = true;
            return this;
        }

        /**
         * 让每次终态回调都抛异常。
         *
         * @return 本对象，便于链式装配
         */
        HookedListener throwOnTerminal() {
            this.throwOnTerminal = true;
            return this;
        }

        @Override
        public void onEvent(JobEvent event) {
            probe.onEvent(event);
            if (event.phase() == JobEvent.Phase.STARTED) {
                onStarted(event);
            } else {
                onTerminal(event);
            }
        }

        /**
         * {@code STARTED} 分支：先读 {@code state()}（应为上一次终态）再登记 / 阻塞。
         *
         * @param event 事件
         */
        private void onStarted(JobEvent event) {
            Map<String, JobEvent> snapshot = JobKit.state();
            JobEvent recorded = snapshot.get(event.taskName());
            synchronized (monitor) {
                presentAtStarted.add(Boolean.valueOf(recorded != null));
                phaseAtStarted.add(recorded == null ? null : recorded.phase());
                startedCallbacks++;
                monitor.notifyAll();
            }
            if (blockOnStarted && blockedOnce.compareAndSet(false, true)) {
                startedEntered.countDown();
                try {
                    startedGate.await(TestJobs.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * 终态分支：先读 {@code state()}（应为本次终态）再登记，最后按需抛异常。
         *
         * @param event 事件
         */
        private void onTerminal(JobEvent event) {
            JobEvent recorded = JobKit.state().get(event.taskName());
            phaseAtTerminal = recorded == null ? null : recorded.phase();
            synchronized (monitor) {
                terminalCallbacks++;
                monitor.notifyAll();
            }
            if (throwOnTerminal) {
                throw new IllegalStateException("listener boom on " + event.taskName());
            }
        }

        /**
         * @param timeoutMillis 超时上限
         * @return 是否已进入首个 {@code STARTED} 回调的阻塞点
         */
        boolean awaitStartedCallback(long timeoutMillis) {
            try {
                return startedEntered.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        /**
         * 释放 {@code STARTED} 回调阻塞。
         */
        void releaseStartedCallback() {
            startedGate.countDown();
        }

        /**
         * @param expected 期望的 {@code STARTED} 回调数
         * @param timeoutMillis 超时上限
         * @return 是否到达期望值
         */
        boolean awaitStartedCallbacks(int expected, long timeoutMillis) {
            return awaitCounter(expected, timeoutMillis, true);
        }

        /**
         * @param expected 期望的终态回调数
         * @param timeoutMillis 超时上限
         * @return 是否到达期望值
         */
        boolean awaitTerminalCallbacks(int expected, long timeoutMillis) {
            return awaitCounter(expected, timeoutMillis, false);
        }

        /**
         * @param index 下标，从 {@code 0} 开始
         * @return 第 {@code index} 次 {@code STARTED} 回调内该任务是否在 {@code state()} 中
         */
        boolean presentAtStarted(int index) {
            synchronized (monitor) {
                return presentAtStarted.get(index).booleanValue();
            }
        }

        /**
         * @return 各次 {@code STARTED} 回调内该任务的终态快照（{@code null} 表示不在 {@code state()} 中）
         */
        List<JobEvent.Phase> phaseAtStarted() {
            synchronized (monitor) {
                return Collections.unmodifiableList(new ArrayList<>(phaseAtStarted));
            }
        }

        /**
         * @return 最近一次终态回调内 {@code state()} 的终态
         */
        JobEvent.Phase phaseAtTerminal() {
            return phaseAtTerminal;
        }

        /**
         * 在监视器上等待计数器到达期望值。
         *
         * @param expected 期望值
         * @param timeoutMillis 超时上限
         * @param started 为 {@code true} 等 {@code STARTED} 计数，否则等终态计数
         * @return 是否到达期望值
         */
        private boolean awaitCounter(int expected, long timeoutMillis, boolean started) {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            synchronized (monitor) {
                while ((started ? startedCallbacks : terminalCallbacks) < expected) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    try {
                        monitor.wait(remaining);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return true;
            }
        }
    }
}
