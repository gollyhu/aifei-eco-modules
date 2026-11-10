/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobInstances;
import cn.hg.aifei.job.support.TestJobContainer;
import cn.hg.aifei.job.support.TestLogs;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 实例获取与多例判定用例（{@code internal.JobInstances}），容器用 {@code support.TestJobContainer} 替身装配。
 *
 * <p>两个入口的失败语义<b>不可混淆</b>：任务实例取不到属<b>配置错误</b>（启动失败）；监听器取不到属<b>正常</b>
 * （观测是可选设施，返回 {@code null}）。两者都认为<b>多例 Bean 是配置错误</b>，但消息区分「任务所在类」
 * 与「监听器」两类定位口径。</p>
 */
public class JobInstancesTest {

    /**
     * 用例统一使用的任务名，用于消息定位断言。
     */
    private static final String TASK = "cn.hg.aifei.job.JobInstancesTest$TaskBean#tick";

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ requireSingle：正常

    /**
     * 容器内有单例实例：原样返回同一引用（任务实例来自容器，事务与日志代理才生效）。
     */
    @Test
    public void requireSingleReturnsRegisteredInstance() {
        TaskBean bean = new TaskBean();
        TestJobContainer container = new TestJobContainer().register(TaskBean.class, bean);
        assertSame("应返回容器内的同一实例", bean, JobInstances.requireSingle(container, TaskBean.class, TASK));
    }

    // ------------------------------------------------------------------ requireSingle：取不到

    /**
     * 容器未注册该类型：启动失败，消息含类全名与任务名。
     */
    @Test
    public void requireSingleMissingInstanceFails() {
        try {
            JobInstances.requireSingle(new TestJobContainer(), TaskBean.class, TASK);
            fail("取不到任务实例应抛 JobException");
        } catch (JobException e) {
            assertTrue("消息应含类全名 " + TaskBean.class.getName() + "，实际：" + e.getMessage(),
                    e.getMessage().contains(TaskBean.class.getName()));
            assertTrue("消息应含任务名 " + TASK + "，实际：" + e.getMessage(), e.getMessage().contains(TASK));
            assertNull("容器正常返回 null 时无底层异常", e.getCause());
        }
    }

    /**
     * 容器取实例时抛异常：同样视为「取不到」，且底层异常保留为 cause。
     */
    @Test
    public void requireSingleContainerFailureIsMissing() {
        try {
            JobInstances.requireSingle(new TestJobContainer().failOnGet(), TaskBean.class, TASK);
            fail("容器抛异常应转为 JobException");
        } catch (JobException e) {
            assertTrue("消息应含任务名 " + TASK + "，实际：" + e.getMessage(), e.getMessage().contains(TASK));
            assertNotNull("容器抛出的异常应保留为 cause", e.getCause());
            assertTrue("cause 应是容器抛出的原始异常", e.getCause() instanceof IllegalStateException);
        }
    }

    // ------------------------------------------------------------------ requireSingle：多例

    /**
     * 任务所在类是多例 Bean：启动失败，消息含任务名、类全名与「必须是单例」的口径。
     */
    @Test
    public void requireSinglePrototypeBeanFails() {
        TestJobContainer container = new TestJobContainer()
                .register(TaskBean.class, new TaskBean())
                .prototype(TaskBean.class);
        try {
            JobInstances.requireSingle(container, TaskBean.class, TASK);
            fail("多例 Bean 应抛 JobException");
        } catch (JobException e) {
            Throwable asThrowable = e;
            assertFalse("启动期异常不得是 IllegalArgumentException",
                    asThrowable instanceof IllegalArgumentException);
            assertFalse("启动期异常不得是 IllegalStateException",
                    asThrowable instanceof IllegalStateException);
            assertTrue("消息应含任务名 " + TASK + "，实际：" + e.getMessage(), e.getMessage().contains(TASK));
            assertTrue("消息应含类全名，实际：" + e.getMessage(),
                    e.getMessage().contains(TaskBean.class.getName()));
            assertTrue("消息应说明必须是单例，实际：" + e.getMessage(),
                    e.getMessage().contains("singleton"));
        }
    }

    // ------------------------------------------------------------------ optionalSingle：取不到即无

    /**
     * 监听器取不到：返回 {@code null}，不抛异常（观测为可选设施）。
     */
    @Test
    public void optionalSingleReturnsNullWhenMissing() {
        assertNull("容器无该类型时应返回 null",
                JobInstances.optionalSingle(new TestJobContainer(), ListenerBean.class));
    }

    /**
     * 容器取实例时抛异常：监听器同样按「取不到」处理，返回 {@code null} 且不抛。
     */
    @Test
    public void optionalSingleReturnsNullWhenContainerFails() {
        assertNull("容器抛异常时监听器应按取不到处理",
                JobInstances.optionalSingle(new TestJobContainer().failOnGet(), ListenerBean.class));
    }

    // ------------------------------------------------------------------ optionalSingle：单例与多例

    /**
     * 监听器是单例：返回容器内的同一引用。
     */
    @Test
    public void optionalSingleReturnsRegisteredInstance() {
        ListenerBean bean = new ListenerBean();
        TestJobContainer container = new TestJobContainer().register(ListenerBean.class, bean);
        assertSame("应返回容器内的同一实例", bean, JobInstances.optionalSingle(container, ListenerBean.class));
    }

    /**
     * 监听器是多例 Bean：启动失败，消息含监听器类全名与「必须是单例」的口径（此处<b>没有</b>任务名）。
     */
    @Test
    public void optionalSinglePrototypeFails() {
        TestJobContainer container = new TestJobContainer()
                .register(ListenerBean.class, new ListenerBean())
                .prototype(ListenerBean.class);
        try {
            JobInstances.optionalSingle(container, ListenerBean.class);
            fail("多例监听器应抛 JobException");
        } catch (JobException e) {
            assertTrue("消息应含监听器类全名，实际：" + e.getMessage(),
                    e.getMessage().contains(ListenerBean.class.getName()));
            assertTrue("消息应说明必须是单例，实际：" + e.getMessage(),
                    e.getMessage().contains("singleton"));
            assertFalse("监听器多例不是任务定位口径，不含任务名", e.getMessage().contains("#tick"));
        }
    }

    // ------------------------------------------------------------------ 测试用类型

    /**
     * 任务所在类（仅用于类型定位）。
     */
    private static final class TaskBean {

        /**
         * 任务方法。
         */
        public void tick() {
        }
    }

    /**
     * 监听器实现类（仅用于类型定位）。
     */
    private static final class ListenerBean implements JobListener {

        @Override
        public void onEvent(JobEvent event) {
        }
    }
}
