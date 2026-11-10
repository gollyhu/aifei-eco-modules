/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.job.internal.JobHolder;
import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobState;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.internal.JobTrigger;
import cn.hg.aifei.job.support.TestLogs;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * {@link JobHolder} 写入通道用例（与 {@code JobKitTest} 互补）：安装覆盖、清除幂等、并发只读不抛。
 *
 * <p>并发用例只用 {@link CountDownLatch} + {@link ExecutorService} 与<b>宽松超时</b>（5s），
 * <b>不使用</b> {@code Thread.sleep}：读线程与写线程通过启动闸门同时放行，读线程跑固定轮次后退出，
 * 断言「无任何异常逃出」与「在超时内完成」。</p>
 */
public class JobHolderTest {

    /**
     * 第一组安装中的任务名。
     */
    private static final String FIRST = "com.acme.FirstJob#run";

    /**
     * 第二组安装中的任务名。
     */
    private static final String SECOND = "com.acme.SecondJob#run";

    /**
     * 并发只读的线程数。
     */
    private static final int READER_COUNT = 4;

    /**
     * 每个读线程的轮次。
     */
    private static final int READER_ROUNDS = 500;

    /**
     * 主线程安装 / 清除的轮次。
     */
    private static final int WRITER_ROUNDS = 200;

    /**
     * 并发用例的宽松超时，单位秒。
     */
    private static final long TIMEOUT_SECONDS = 5L;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    /**
     * 每例从「未安装」状态开始。
     */
    @Before
    public void setUp() {
        JobHolder.clear();
    }

    /**
     * 每例结束后清除安装。
     */
    @After
    public void tearDown() {
        JobHolder.clear();
    }

    // ------------------------------------------------------------------ 安装与覆盖

    /**
     * 再次安装覆盖既有安装：观测面立即切换到新一组数据。
     */
    @Test
    public void installOverridesPreviousInstallation() {
        JobHolder.install(registry(task(FIRST)), new JobState());
        assertEquals("首次安装后应看到 1 个任务", 1, JobHolder.names().size());
        assertTrue("首次安装后应含 FIRST", JobHolder.names().contains(FIRST));

        JobState second = new JobState();
        second.record(JobEvent.of(SECOND, 1609430400000L, JobEvent.Phase.SUCCESS, null));
        JobHolder.install(registry(task(SECOND)), second);

        Set<String> names = JobHolder.names();
        assertEquals("再次安装应覆盖既有安装", 1, names.size());
        assertTrue("覆盖后应只剩 SECOND，实际：" + names, names.contains(SECOND));
        Map<String, JobEvent> state = JobHolder.state();
        assertEquals("覆盖后状态记录应来自新安装", 1, state.size());
        assertTrue("覆盖后状态记录应含 SECOND", state.containsKey(SECOND));
    }

    /**
     * 安装空注册表也是合法安装：观测面为空但不抛异常。
     */
    @Test
    public void installEmptyRegistryIsAllowed() {
        JobHolder.install(new JobRegistry(null), new JobState());
        assertTrue("空注册表安装后 names() 应为空", JobHolder.names().isEmpty());
        assertTrue("空注册表安装后 state() 应为空", JobHolder.state().isEmpty());
    }

    // ------------------------------------------------------------------ 清除幂等

    /**
     * 清除幂等：连续多次清除不抛异常，且观测面保持为空。
     */
    @Test
    public void clearIsIdempotent() {
        JobHolder.install(registry(task(FIRST)), new JobState());
        JobHolder.clear();
        JobHolder.clear();
        JobHolder.clear();
        assertTrue("多次清除后 names() 应为空", JobHolder.names().isEmpty());
        assertTrue("多次清除后 state() 应为空", JobHolder.state().isEmpty());
    }

    /**
     * 从未安装即清除也是安全的。
     */
    @Test
    public void clearBeforeInstallIsSafe() {
        JobHolder.clear();
        assertTrue("未安装即清除后 names() 应为空", JobHolder.names().isEmpty());
        assertTrue("未安装即清除后 state() 应为空", JobHolder.state().isEmpty());
    }

    // ------------------------------------------------------------------ 并发只读

    /**
     * 并发只读与「安装 / 清除」交错发生时不抛异常、不死锁。
     */
    @Test
    public void concurrentReadsNeverThrow() throws Exception {
        final JobRegistry registry = registry(task(FIRST), task(SECOND));
        final JobState state = new JobState();
        state.record(JobEvent.of(FIRST, 1609430400000L, JobEvent.Phase.SUCCESS, null));

        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(READER_COUNT);
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ExecutorService readers = Executors.newFixedThreadPool(READER_COUNT);
        try {
            for (int i = 0; i < READER_COUNT; i++) {
                readers.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            start.await();
                            for (int round = 0; round < READER_ROUNDS; round++) {
                                JobHolder.names();
                                JobHolder.state();
                            }
                        } catch (Throwable t) {
                            failures.add(t);
                        } finally {
                            done.countDown();
                        }
                    }
                });
            }
            start.countDown();
            for (int round = 0; round < WRITER_ROUNDS; round++) {
                JobHolder.install(registry, state);
                JobHolder.clear();
            }
            assertTrue("并发只读应在 " + TIMEOUT_SECONDS + "s 内完成",
                    done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            readers.shutdownNow();
            assertTrue("线程池应能收敛", readers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        assertTrue("并发只读不得抛异常，实际：" + failures, failures.isEmpty());
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * @param tasks 任务定义
     * @return 任务注册表
     */
    private static JobRegistry registry(JobTask... tasks) {
        return new JobRegistry(Arrays.asList(tasks));
    }

    /**
     * 构造一个只用于占位的任务定义（注册表只读取其任务名）。
     *
     * @param name 任务名
     * @return 任务定义
     */
    private static JobTask task(String name) {
        try {
            Method method = SampleJob.class.getDeclaredMethod("tick");
            return new JobTask(name, new SampleJob(), method, JobTrigger.fixedRate(1000L, 0L, name));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试用任务方法缺失", e);
        }
    }

    /**
     * 占位任务类。
     */
    private static final class SampleJob {

        /**
         * 任务方法。
         */
        public void tick() {
        }
    }
}
