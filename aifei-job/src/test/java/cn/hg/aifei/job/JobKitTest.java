/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobHolder;
import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobState;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.internal.JobTrigger;
import cn.hg.aifei.job.support.TestLogs;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 观测门面用例（{@link JobKit}）：<b>未启动 / 已停机时返回空集合且不抛异常</b>，安装后可读，清除后回到空集合。
 *
 * <p>「不抛异常」是模块对「{@code XxxKit} 未装配即失败」的唯一有意让步：管理端点与演示页在停机窗口会继续轮询，
 * 此时开始抛异常只会制造噪声。返回值一律不可修改，且不得暴露内部活视图。</p>
 */
public class JobKitTest {

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
     * 装配空日志工厂，并确保本类用例从「未安装」状态开始。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    /**
     * 每例都从「未安装」状态开始。
     */
    @Before
    public void setUp() {
        JobHolder.clear();
    }

    /**
     * 每例结束后清除安装，避免污染其它用例。
     */
    @After
    public void tearDown() {
        JobHolder.clear();
    }

    // ------------------------------------------------------------------ 未启动 / 已清除

    /**
     * 未安装时两个方法都返回空集合，<b>不抛异常</b>。
     */
    @Test
    public void observationBeforeStartIsEmptyAndQuiet() {
        Set<String> names = JobKit.names();
        Map<String, JobEvent> state = JobKit.state();
        assertTrue("未启动时 names() 应为空", names.isEmpty());
        assertTrue("未启动时 state() 应为空", state.isEmpty());
        assertEquals("未启动时 names() 大小应为 0", 0, names.size());
        assertEquals("未启动时 state() 大小应为 0", 0, state.size());
    }

    /**
     * 反复调用未安装的门面不会失败（停机窗口轮询场景）。
     */
    @Test
    public void repeatedReadsWithoutInstallAreQuiet() {
        for (int i = 0; i < 100; i++) {
            assertTrue(JobKit.names().isEmpty());
            assertTrue(JobKit.state().isEmpty());
        }
    }

    // ------------------------------------------------------------------ 安装后可读

    /**
     * 安装后 {@code names()} 可读，且按任务名排序。
     */
    @Test
    public void namesAreReadableAfterInstall() {
        JobHolder.install(registry(task(TASK_B), task(TASK_A)), new JobState());
        List<String> names = new ArrayList<>(JobKit.names());
        assertEquals("应看到 2 个已注册任务", 2, names.size());
        assertEquals("第 1 个任务名", TASK_A, names.get(0));
        assertEquals("第 2 个任务名", TASK_B, names.get(1));
    }

    /**
     * 安装后 {@code state()} 反映已记账的终态。
     */
    @Test
    public void stateIsReadableAfterInstall() {
        JobState state = new JobState();
        state.record(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        JobHolder.install(registry(task(TASK_A)), state);
        Map<String, JobEvent> snapshot = JobKit.state();
        assertEquals("应看到 1 条终态", 1, snapshot.size());
        assertSame("阶段应为 SUCCESS", JobEvent.Phase.SUCCESS, snapshot.get(TASK_A).phase());
        assertEquals("触发时间应原样可见", TIME, snapshot.get(TASK_A).triggerTime());
    }

    /**
     * 清除后回到空集合，且不抛异常。
     */
    @Test
    public void clearRestoresEmptyObservation() {
        JobState state = new JobState();
        state.record(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        JobHolder.install(registry(task(TASK_A)), state);
        assertEquals("安装后应可见 1 个任务", 1, JobKit.names().size());

        JobHolder.clear();
        assertTrue("清除后 names() 应为空", JobKit.names().isEmpty());
        assertTrue("清除后 state() 应为空", JobKit.state().isEmpty());
    }

    // ------------------------------------------------------------------ 不可修改

    /**
     * {@code names()} 的结果不可修改。
     */
    @Test
    public void namesAreUnmodifiable() {
        JobHolder.install(registry(task(TASK_A)), new JobState());
        Set<String> names = JobKit.names();
        try {
            names.add(TASK_B);
            fail("names() 应为不可修改视图");
        } catch (UnsupportedOperationException expected) {
            // 预期：不可修改视图拒绝写入
        }
    }

    /**
     * {@code state()} 的结果不可修改。
     */
    @Test
    public void stateIsUnmodifiable() {
        JobState state = new JobState();
        state.record(JobEvent.of(TASK_A, TIME, JobEvent.Phase.SUCCESS, null));
        JobHolder.install(registry(task(TASK_A)), state);
        Map<String, JobEvent> snapshot = JobKit.state();
        try {
            snapshot.put(TASK_B, JobEvent.of(TASK_B, TIME, JobEvent.Phase.SUCCESS, null));
            fail("state() 应为不可修改视图");
        } catch (UnsupportedOperationException expected) {
            // 预期：不可修改视图拒绝写入
        }
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
