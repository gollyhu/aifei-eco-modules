/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
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
 * 触发与执行分离（设计决策 7 / §5.4 线程归属 / A07）：一个长阻塞任务执行中，另一个短周期任务<b>仍能到点触发</b>。
 *
 * <p>若唤醒层被任务体拖住（例如拒绝策略改成 {@code CallerRunsPolicy}、或任务体跑在唤醒线程上），短周期任务不可能
 * 在阻塞期间连续触发；此外两者的任务体必然跑在<b>不同的执行层线程</b>上（比对 {@link Thread} 引用，不依赖线程名）。</p>
 */
public class LongTaskDoesNotBlockWakeupTest {

    /**
     * 触发间隔，单位毫秒。
     */
    private static final long PERIOD = 20L;

    /**
     * 短周期任务在阻塞任务执行期间要求达到的触发次数。
     */
    private static final int TICKS_WHILE_BLOCKED = 3;

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

    /**
     * 长任务阻塞期间短周期任务照常到点触发。
     */
    @Test
    public void longTaskDoesNotBlockOtherTriggers() {
        TestJobs.Task blocker = new TestJobs.Task().block();
        TestJobs.Task ticker = new TestJobs.Task();
        bodies.add(blocker);
        bodies.add(ticker);
        JobTask blockerTask = TestJobs.fixedRate(blocker, "alpha", PERIOD, 0L);
        JobTask tickerTask = TestJobs.fixedRate(ticker, "beta", PERIOD, 0L);
        start(blockerTask, tickerTask);

        assertTrue("长任务应开始执行", probe.awaitPhase(blockerTask.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("长任务阻塞期间短周期任务应至少触发 " + TICKS_WHILE_BLOCKED + " 次",
                probe.awaitPhase(tickerTask.name(), JobEvent.Phase.STARTED, TICKS_WHILE_BLOCKED,
                        TestJobs.TIMEOUT_MILLIS));

        assertEquals("长任务应仍处于首次执行中（未被其它触发打断）", 1, blocker.runs());
        assertEquals("长任务不得重叠执行", 1, blocker.peakInFlight());
        assertNotSame("两个任务应跑在不同的执行层线程上", blocker.bodyThread(), ticker.bodyThread());
        assertTrue("短周期任务应在长任务仍在执行时完成上述触发", ticker.runs() >= TICKS_WHILE_BLOCKED);
    }

    // ------------------------------------------------------------------ 装配

    /**
     * 手工装配两线程执行层的调度内核并启动。
     *
     * @param tasks 任务定义
     */
    private void start(JobTask... tasks) {
        JobRegistry registry = TestJobs.registry(tasks);
        JobHolder.install(registry, state);
        scheduler = new JobScheduler(TestJobs.config(2, 4, 500L), registry, state, new JobEvents(probe),
                new JobSchedule(new QuartzCronSupport()));
        scheduler.start();
    }
}
