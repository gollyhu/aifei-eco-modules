/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.job.internal.JobEvents;
import cn.hg.aifei.job.support.EventProbe;
import cn.hg.aifei.job.support.TestLogs;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 事件分发用例（{@code internal.JobEvents}）：无监听器静默、<b>同步</b>在调用线程回调、回调异常被吞掉、
 * 事件原样传递、同任务 {@code STARTED → 终态} 顺序。
 *
 * <p>监听器属可选观测设施，<b>绝不能</b>影响任务执行：故 {@code dispatch} 对回调抛出的任何 {@link Throwable}
 * 就地捕获记录、绝不上抛；分发是同步直调，没有队列与监听线程池，回调线程即产生事件的线程。</p>
 */
public class JobEventsTest {

    /**
     * 任务 A 的任务名。
     */
    private static final String TASK_A = "com.acme.AJob#alpha";

    /**
     * 任务 B 的任务名。
     */
    private static final String TASK_B = "com.acme.BJob#beta";

    /**
     * 用例统一使用的触发时间，epoch 毫秒。
     */
    private static final long TIME = 1609430400000L;

    /**
     * 分发用例的宽松超时，单位秒。
     */
    private static final long TIMEOUT_SECONDS = 5L;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ 无监听器 / null 事件

    /**
     * 没有监听器时 {@code dispatch} 静默返回（事件照常产生与记账，只是没有送达方）。
     */
    @Test
    public void dispatchWithoutListenerIsQuiet() {
        JobEvents events = new JobEvents(null);
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        events.dispatch(null);
    }

    /**
     * 有监听器但事件为 {@code null} 时直接忽略，不回调。
     */
    @Test
    public void dispatchNullEventIsIgnored() {
        EventProbe probe = new EventProbe();
        new JobEvents(probe).dispatch(null);
        assertEquals("null 事件不应送达监听器", 0, probe.size());
    }

    // ------------------------------------------------------------------ 同步与线程归属

    /**
     * 回调在<b>调用线程</b>上同步执行：比对 {@link Thread} 引用，不依赖线程名。
     */
    @Test
    public void listenerRunsOnCallingThread() {
        EventProbe probe = new EventProbe();
        JobEvents events = new JobEvents(probe);
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        assertEquals("应送达 1 个事件", 1, probe.size());
        assertSame("回调应在调用线程上执行", Thread.currentThread(), probe.callbackThread(0));
    }

    /**
     * 在另一个线程上分发时，回调线程就是那个线程（同步直调，无异步派发）。
     */
    @Test
    public void listenerRunsOnTheProducingThread() throws Exception {
        final EventProbe probe = new EventProbe();
        final JobEvents events = new JobEvents(probe);
        final AtomicReference<Thread> dispatcher = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    dispatcher.set(Thread.currentThread());
                    events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.STARTED, null));
                } finally {
                    done.countDown();
                }
            }
        }, "test-job-events-worker");
        worker.start();
        assertTrue("分发应在 " + TIMEOUT_SECONDS + "s 内完成",
                done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertSame("回调线程应是产生事件的那个线程", dispatcher.get(), probe.callbackThread(0));
    }

    // ------------------------------------------------------------------ 异常隔离

    /**
     * 监听器抛 {@code RuntimeException} 时 {@code dispatch} 不上抛。
     */
    @Test
    public void listenerRuntimeExceptionIsSwallowed() {
        JobEvents events = new JobEvents(new JobListener() {
            @Override
            public void onEvent(JobEvent event) {
                throw new IllegalStateException("listener boom");
            }
        });
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
    }

    /**
     * 监听器抛 {@link Error} 时同样被吞掉：{@code catch Throwable} 的口径，不仅是 {@code Exception}。
     */
    @Test
    public void listenerErrorIsSwallowed() {
        JobEvents events = new JobEvents(new JobListener() {
            @Override
            public void onEvent(JobEvent event) {
                throw new AssertionError("listener error");
            }
        });
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.FAILED, new IllegalStateException("task boom")));
    }

    /**
     * 一次回调失败不影响后续事件送达。
     */
    @Test
    public void listenerFailureDoesNotStopLaterDispatch() {
        final AtomicInteger received = new AtomicInteger();
        JobEvents events = new JobEvents(new JobListener() {
            @Override
            public void onEvent(JobEvent event) {
                if (received.incrementAndGet() == 1) {
                    throw new IllegalStateException("first delivery fails");
                }
            }
        });
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.STARTED, null));
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        assertEquals("第二次分发仍应送达监听器", 2, received.get());
    }

    // ------------------------------------------------------------------ 事件原样传递

    /**
     * 事件对象原样传递：监听器拿到的是<b>同一个</b>实例，不被包装、不被复制。
     */
    @Test
    public void eventIsPassedThroughAsIs() {
        EventProbe probe = new EventProbe();
        JobEvent event = JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null);
        new JobEvents(probe).dispatch(event);
        assertSame("应原样传递同一事件实例", event, probe.events().get(0));
    }

    /**
     * 同一任务连续两次分发：监听器按 {@code STARTED → 终态} 的顺序收到。
     */
    @Test
    public void sameTaskArrivesStartedThenTerminal() {
        EventProbe probe = new EventProbe();
        JobEvents events = new JobEvents(probe);
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.STARTED, null));
        events.dispatch(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        List<JobEvent.Phase> phases = probe.phasesOf(TASK_A);
        assertEquals("应有 2 个事件", 2, phases.size());
        assertEquals("顺序应为 STARTED → SUCCESS",
                Arrays.asList(JobEvent.Phase.STARTED, JobEvent.Phase.SUCCESS), phases);
        assertEquals("另一任务不应收到事件", 0, probe.count(TASK_B, JobEvent.Phase.SUCCESS));
    }

    // ------------------------------------------------------------------ 装配

    /**
     * {@code assemble} 按入参装配事件，且每次都是新实例。
     */
    @Test
    public void assembleBuildsNewEventEachTime() {
        JobEvent first = JobEvents.assemble(JobEvent.Phase.SKIPPED, TASK_B, TIME, null);
        JobEvent second = JobEvents.assemble(JobEvent.Phase.SKIPPED, TASK_B, TIME, null);
        assertEquals("任务名应原样保留", TASK_B, first.taskName());
        assertEquals("触发时间应原样保留", TIME, first.triggerTime());
        assertSame("阶段应原样保留", JobEvent.Phase.SKIPPED, first.phase());
        assertNotSame("每次装配都应是新实例", first, second);
    }
}
