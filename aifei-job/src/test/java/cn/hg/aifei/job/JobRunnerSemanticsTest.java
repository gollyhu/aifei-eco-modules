/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 调度内核闭环语义（设计 §6 契约表 / 决策 4、5、6）：注册 → 触发 → 执行、同一任务不重叠、任务异常隔离且续排不断链、
 * 任务体异常绝不外抛、固定延迟触发同样闭环。
 *
 * <p>全部等待为 {@code CountDownLatch} / 监视器 + <b>5s 宽松超时</b>；{@code period} 取 20ms 级以缩短用例时长；
 * 「不重叠」用<b>并发峰值</b>与「阻塞期间不产生第二次 STARTED」两条互补证据断言，不依赖精确毫秒时序。</p>
 */
public class JobRunnerSemanticsTest {

    /**
     * 触发间隔，单位毫秒（20ms 级：既让用例在百毫秒内闭环，又远大于同 JVM 内一次提交的开销）。
     */
    private static final long PERIOD = 20L;

    /**
     * 执行层线程数。
     */
    private static final int POOL_SIZE = 2;

    /**
     * 执行层队列容量。
     */
    private static final int POOL_QUEUE = 4;

    /**
     * 停机等待上限，单位毫秒。
     */
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 500L;

    /**
     * 本类装配过的任务体，{@code @After} 逐个解除阻塞。
     */
    private final List<TestJobs.Task> bodies = new ArrayList<>();

    /**
     * 未捕获异常收集器：任务体异常外抛会落到这里。
     */
    private final List<Throwable> uncaught = Collections.synchronizedList(new ArrayList<Throwable>());

    /**
     * 调度器；每例自建。
     */
    private JobScheduler scheduler;

    /**
     * 状态记账；与生产同一实例被 {@link JobHolder} 安装，故 {@code JobKit} 可观测。
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
     * 安装未捕获异常收集器并新建探针 / 状态记录。
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
     * 解除全部阻塞、停机、清观测面、还原处理器，并断言全程无未捕获异常（决策 6 / 决策 8 第 2 条）。
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
        assertTrue("任务体异常不得外抛，实际收到：" + uncaught, uncaught.isEmpty());
    }

    // ------------------------------------------------------------------ 注册 → 触发 → 执行

    /**
     * 闭环：已注册任务按 {@code initialDelay} 到点触发并跑完任务体，{@code STARTED → SUCCESS} 成对到达，
     * 且 {@code names()} 可见该任务（A04）。
     */
    @Test
    public void registeredTaskIsTriggeredAndExecuted() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(POOL_SIZE, POOL_QUEUE, SHUTDOWN_TIMEOUT_MILLIS, task);

        assertTrue("应在 5s 内收到至少两次 STARTED", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("应在 5s 内收到至少两次 SUCCESS", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 2,
                TestJobs.TIMEOUT_MILLIS));

        assertTrue("任务体应至少执行两次，实际 " + body.runs(), body.runs() >= 2);
        assertTrue("观测面应可见已注册任务", JobKit.names().contains(task.name()));
    }

    /**
     * 同一任务不重叠（决策 4）：任务体阻塞期间<b>不产生第二次 STARTED</b>，并发峰值恒为 1。
     *
     * <p>结构依据：同一任务恒至多一个待触发实例，下一个实例只在执行结束（{@code finally}）或提交被拒时就地投递。</p>
     */
    @Test
    public void sameTaskNeverOverlaps() {
        TestJobs.Task body = new TestJobs.Task().block();
        bodies.add(body);
        JobTask task = TestJobs.fixedRate(body, "alpha", PERIOD, 0L);
        start(POOL_SIZE, POOL_QUEUE, SHUTDOWN_TIMEOUT_MILLIS, task);

        assertTrue("应收到首次 STARTED", probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 1,
                TestJobs.TIMEOUT_MILLIS));

        // 稳定等待：period 为 20ms，等待 300ms 期间若有重叠，STARTED 必然增长到 2 次以上。
        assertFalse("任务体阻塞期间不得出现第二次触发（不重叠）",
                probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2, 3 * TestJobs.STABLE_WAIT_MILLIS / 2));
        assertEquals("任务体应只执行了 1 次", 1, body.runs());

        body.release(1);
        assertTrue("释放后应跑完并记 SUCCESS", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 1,
                TestJobs.TIMEOUT_MILLIS));
        assertEquals("并发峰值应恒为 1", 1, body.peakInFlight());
    }

    // ------------------------------------------------------------------ 异常隔离

    /**
     * 异常隔离 + 断链保护（决策 6）：任务体每次抛异常都记 {@code FAILED}（带原因），该任务<b>后续周期继续触发</b>，
     * 同时不影响其他任务。
     */
    @Test
    public void failureIsIsolatedAndChainContinues() {
        IllegalStateException boom = new IllegalStateException("boom");
        TestJobs.Task bad = new TestJobs.Task().failAlways(boom);
        TestJobs.Task good = new TestJobs.Task();
        bodies.add(bad);
        bodies.add(good);
        JobTask badTask = TestJobs.fixedRate(bad, "alpha", PERIOD, 0L);
        JobTask goodTask = TestJobs.fixedRate(good, "beta", PERIOD, 0L);
        start(POOL_SIZE, POOL_QUEUE, SHUTDOWN_TIMEOUT_MILLIS, badTask, goodTask);

        assertTrue("异常任务应至少失败两次（续排不断链）",
                probe.awaitPhase(badTask.name(), JobEvent.Phase.FAILED, 2, TestJobs.TIMEOUT_MILLIS));
        assertTrue("正常任务应照常触发至少两次",
                probe.awaitPhase(goodTask.name(), JobEvent.Phase.STARTED, 2, TestJobs.TIMEOUT_MILLIS));

        JobEvent failed = firstFailed(probe.eventsOf(badTask.name()));
        assertEquals(JobEvent.Phase.FAILED, failed.phase());
        assertNotNull("FAILED 事件必须带异常原因", failed.error());
        assertSame("事务体原因应原样解包（不经反射包装）", boom, failed.error());
        assertTrue("异常不得阻止续排，实际失败次数 " + bad.runs(), bad.runs() >= 2);
    }

    /**
     * 任务体异常不外抛：{@code FAILED} 已记账且未捕获异常收集器为空（执行层线程不得带异常结束）。
     */
    @Test
    public void taskExceptionNeverEscapesExecutor() {
        TestJobs.Task bad = new TestJobs.Task().failAlways(new IllegalArgumentException("bad body"));
        bodies.add(bad);
        JobTask task = TestJobs.fixedRate(bad, "alpha", PERIOD, 0L);
        start(POOL_SIZE, POOL_QUEUE, SHUTDOWN_TIMEOUT_MILLIS, task);

        assertTrue("应至少失败两次", probe.awaitPhase(task.name(), JobEvent.Phase.FAILED, 2,
                TestJobs.TIMEOUT_MILLIS));
        assertTrue("任务体异常不得外抛，实际收到：" + uncaught, uncaught.isEmpty());
        assertEquals("异常不外抛，故不应有 SUCCESS", 0, probe.count(task.name(), JobEvent.Phase.SUCCESS));
    }

    // ------------------------------------------------------------------ 固定延迟闭环

    /**
     * 固定延迟触发同样闭环：{@code @FixedDelay} 任务按「上次结束 + 延迟」触发至少两次。
     */
    @Test
    public void fixedDelayTaskKeepsTriggering() {
        TestJobs.Task body = new TestJobs.Task();
        bodies.add(body);
        JobTask task = TestJobs.fixedDelay(body, "epsilon", PERIOD, 0L);
        start(POOL_SIZE, POOL_QUEUE, SHUTDOWN_TIMEOUT_MILLIS, task);

        assertTrue("固定延迟任务应至少触发两次",
                probe.awaitPhase(task.name(), JobEvent.Phase.STARTED, 2, TestJobs.TIMEOUT_MILLIS));
        assertTrue("应至少成功两次", probe.awaitPhase(task.name(), JobEvent.Phase.SUCCESS, 2,
                TestJobs.TIMEOUT_MILLIS));
    }

    // ------------------------------------------------------------------ 装配

    /**
     * @param events 某任务的事件序列
     * @return 其中第一条 {@code FAILED} 事件
     */
    private static JobEvent firstFailed(List<JobEvent> events) {
        for (JobEvent event : events) {
            if (event.phase() == JobEvent.Phase.FAILED) {
                return event;
            }
        }
        throw new AssertionError("应有 FAILED 事件，实际：" + events);
    }

    /**
     * 手工装配调度内核并启动；同时把注册表与状态记录安装到 {@link JobHolder}，使 {@code JobKit} 可观测。
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
}
