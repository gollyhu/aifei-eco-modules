/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobConfig;
import cn.hg.aifei.job.internal.JobInstances;
import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobScanner;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.internal.JobTrigger;
import cn.hg.aifei.job.local.QuartzCronSupport;
import cn.hg.aifei.job.support.StubCronCalculator;
import cn.hg.aifei.job.support.TestJobContainer;
import cn.hg.aifei.job.support.TestLogs;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 启动期异常契约用例（汇总断言）：所有启动期失败一律是 {@link JobException}，
 * <b>不是</b> {@code IllegalArgumentException} / {@code IllegalStateException}，且消息必含定位信息。
 *
 * <p>三类定位口径：<b>方法全名</b>（即任务名「类全名#方法名」）、<b>键名与原值</b>（配置与注解属性）、
 * <b>表达式原文</b>（cron）；底层异常（如表达式解析失败）保留在 {@link JobException#getCause()}。
 * 空白 cron 表达式属「未设置」而非「解析失败」，<b>无</b> cause 是预期行为，故此处不断言它的 cause。</p>
 *
 * <p>本类不重复其它用例类的细节断言，只锁「异常类型 + 定位信息 + cause」这条统一契约。</p>
 */
public class StartupExceptionContractTest {

    /**
     * 用例统一使用的任务名，用于消息定位断言。
     */
    private static final String TASK = "com.acme.OrderJob#clean";

    /**
     * 用例统一使用的解析包名（只用于「扫不到任务」的消息）。
     */
    private static final String SCAN_PACKAGE = "cn.hg.aifei.job.contract";

    /**
     * 替身认定为合法的表达式。
     */
    private static final String VALID_CRON = "0 0/5 * * * ?";

    /**
     * 真实 Quartz 认定为非法的表达式（分钟越界）。
     */
    private static final String ILLEGAL_CRON = "0 70 * * * ?";

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ 配置域

    /**
     * 配置失败全部是 {@link JobException}，消息含键名与原值。
     */
    @Test
    public void configFailuresAreJobException() {
        JobException missing = expectJobException("缺失必填键",
                () -> JobConfig.parse(new Properties()));
        assertLocates(missing, JobConfig.SCAN_PACKAGES_KEY);

        JobException blank = expectJobException("必填键为空串",
                () -> JobConfig.parse(props(JobConfig.SCAN_PACKAGES_KEY, "  ")));
        assertLocates(blank, JobConfig.SCAN_PACKAGES_KEY);

        JobException blankItem = expectJobException("扫描包含空项",
                () -> JobConfig.parse(props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a,,cn.acme.b")));
        assertLocates(blankItem, JobConfig.SCAN_PACKAGES_KEY, "cn.acme.a,,cn.acme.b");

        JobException poolSize = expectJobException("pool.size 越界",
                () -> JobConfig.parse(withProperty(JobConfig.POOL_SIZE_KEY, "0")));
        assertLocates(poolSize, JobConfig.POOL_SIZE_KEY, "0");

        JobException poolQueue = expectJobException("pool.queue 越界",
                () -> JobConfig.parse(withProperty(JobConfig.POOL_QUEUE_KEY, "-1")));
        assertLocates(poolQueue, JobConfig.POOL_QUEUE_KEY, "-1");

        JobException timeout = expectJobException("shutdown.timeoutMillis 为负",
                () -> JobConfig.parse(withProperty(JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, "-1")));
        assertLocates(timeout, JobConfig.SHUTDOWN_TIMEOUT_MILLIS_KEY, "-1");
    }

    /**
     * 数值无法解析：同样是 {@link JobException}，键名与原值在消息里，解析异常在 cause。
     */
    @Test
    public void nonNumericConfigKeepsCause() {
        JobException e = expectJobException("pool.size 非整数",
                () -> JobConfig.parse(withProperty(JobConfig.POOL_SIZE_KEY, "abc")));
        assertLocates(e, JobConfig.POOL_SIZE_KEY, "abc");
        assertNotNull("数值解析失败应保留底层异常为 cause", e.getCause());
        assertTrue("cause 应是解析异常", e.getCause() instanceof NumberFormatException);
    }

    // ------------------------------------------------------------------ 触发注解属性

    /**
     * 注解属性越界全部是 {@link JobException}，消息含属性名与原值。
     */
    @Test
    public void triggerFailuresAreJobException() {
        assertLocates(expectJobException("period 为 0（未设置）",
                () -> JobTrigger.fixedRate(0L, 0L, TASK)), "period", "0");
        assertLocates(expectJobException("period 为负",
                () -> JobTrigger.fixedRate(-5L, 0L, TASK)), "period", "-5");
        assertLocates(expectJobException("delay 为 0（未设置）",
                () -> JobTrigger.fixedDelay(0L, 0L, TASK)), "delay", "0");
        assertLocates(expectJobException("delay 为负",
                () -> JobTrigger.fixedDelay(-7L, 0L, TASK)), "delay", "-7");
        assertLocates(expectJobException("initialDelay 为负（固定频率）",
                () -> JobTrigger.fixedRate(1000L, -1L, TASK)), "initialDelay", "-1");
        assertLocates(expectJobException("initialDelay 为负（固定延迟）",
                () -> JobTrigger.fixedDelay(1000L, -1L, TASK)), "initialDelay", "-1");
    }

    /**
     * cron 表达式为空白：{@link JobException} + 任务名；<b>无</b> cause 属预期（未设置而非解析失败）。
     */
    @Test
    public void blankCronFailureHasNoCause() {
        JobException e = expectJobException("cron 表达式空白", () -> JobTrigger.cron("   ", TASK));
        assertLocates(e, TASK);
        assertNull("空白表达式是「未设置」，不应附带底层解析异常", e.getCause());
    }

    // ------------------------------------------------------------------ cron 解析失败

    /**
     * 表达式无法解析：消息含表达式<b>原文</b>与方法全名，Quartz 的解析异常保留为 cause。
     */
    @Test
    public void illegalCronKeepsCause() {
        QuartzCronSupport cron = new QuartzCronSupport();
        JobException e = expectJobException("非法 cron 表达式", () -> cron.validate(ILLEGAL_CRON, TASK));
        assertLocates(e, TASK, ILLEGAL_CRON);
        assertNotNull("表达式解析失败应保留底层异常为 cause", e.getCause());
        assertTrue("cause 应是 Quartz 解析异常", e.getCause() instanceof java.text.ParseException);
    }

    // ------------------------------------------------------------------ 扫描域

    /**
     * 签名非法与多触发注解：{@link JobException} + 方法全名。
     */
    @Test
    public void signatureAndAnnotationFailuresAreJobException() {
        assertLocates(expectJobException("static 方法", () -> scan(StaticJob.class)), name(StaticJob.class, "bad"));
        assertLocates(expectJobException("有返回值方法", () -> scan(ReturnValueJob.class)),
                name(ReturnValueJob.class, "bad"));
        assertLocates(expectJobException("参数非法方法", () -> scan(StringParameterJob.class)),
                name(StringParameterJob.class, "bad"));
        assertLocates(expectJobException("多触发注解", () -> scan(MultipleTriggerJob.class)),
                name(MultipleTriggerJob.class, "bad"));
    }

    /**
     * 扫不到任务：{@link JobException} + 扫描包列表（无方法可定位）。
     */
    @Test
    public void noTaskFoundIsJobException() {
        assertLocates(expectJobException("扫不到任务", () -> scan(NoAnnotationJob.class)), SCAN_PACKAGE);
    }

    /**
     * 扫描期表达式非法（真实 Quartz）：消息含方法全名与表达式原文，cause 为解析异常。
     */
    @Test
    public void scanIllegalCronIsJobExceptionWithCause() {
        TestJobContainer container = new TestJobContainer().register(IllegalCronJob.class, new IllegalCronJob());
        JobException e = expectJobException("扫描到非法表达式", () -> new JobScanner(container,
                new QuartzCronSupport(), Collections.singletonList(SCAN_PACKAGE))
                .scan(Collections.<Class<?>>singletonList(IllegalCronJob.class)));
        assertLocates(e, name(IllegalCronJob.class, "bad"), ILLEGAL_CRON);
        assertNotNull("表达式解析失败应保留底层异常为 cause", e.getCause());
    }

    // ------------------------------------------------------------------ 实例解析域

    /**
     * 任务实例取不到：{@link JobException}，消息含类全名与任务名。
     */
    @Test
    public void missingInstanceIsJobException() {
        JobException e = expectJobException("任务实例取不到",
                () -> JobInstances.requireSingle(new TestJobContainer(), TaskBean.class, TASK));
        assertLocates(e, TASK, TaskBean.class.getName());
        assertNull("容器直接返回 null 时无底层异常", e.getCause());
    }

    /**
     * 容器抛异常：转为 {@link JobException} 且底层异常保留为 cause。
     */
    @Test
    public void containerFailureIsJobExceptionWithCause() {
        JobException e = expectJobException("容器取实例抛异常",
                () -> JobInstances.requireSingle(new TestJobContainer().failOnGet(), TaskBean.class, TASK));
        assertLocates(e, TASK, TaskBean.class.getName());
        assertNotNull("容器抛出的异常应保留为 cause", e.getCause());
    }

    /**
     * 任务所在类是多例：{@link JobException} + 任务名 + 类全名 + 单例要求。
     */
    @Test
    public void prototypeTaskBeanIsJobException() {
        TestJobContainer container = new TestJobContainer()
                .register(TaskBean.class, new TaskBean())
                .prototype(TaskBean.class);
        JobException e = expectJobException("任务所在类为多例",
                () -> JobInstances.requireSingle(container, TaskBean.class, TASK));
        assertLocates(e, TASK, TaskBean.class.getName(), "singleton");
    }

    /**
     * 监听器为多例：{@link JobException} + 监听器类全名 + 单例要求。
     */
    @Test
    public void prototypeListenerIsJobException() {
        TestJobContainer container = new TestJobContainer()
                .register(ListenerBean.class, new ListenerBean())
                .prototype(ListenerBean.class);
        JobException e = expectJobException("监听器为多例",
                () -> JobInstances.optionalSingle(container, ListenerBean.class));
        assertLocates(e, ListenerBean.class.getName(), "singleton");
    }

    // ------------------------------------------------------------------ 注册表域

    /**
     * 同一任务名重复注册：{@link JobException} + 任务名。
     */
    @Test
    public void duplicateTaskIsJobException() {
        JobTask task = task(TASK);
        JobException e = expectJobException("重复任务名",
                () -> new JobRegistry(Arrays.asList(task, task)));
        assertLocates(e, TASK);
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * 断言某个动作以 {@link JobException} 失败，并排除「其它异常类型伪装」的可能。
     *
     * @param scenario 场景描述，仅用于失败消息
     * @param action 触发失败的动作
     * @return 捕获到的启动期异常
     */
    private static JobException expectJobException(String scenario, Action action) {
        try {
            action.run();
            fail(scenario + " 应以 JobException 失败，实际正常返回");
            return null;
        } catch (JobException e) {
            assertEquals(scenario + " 应抛唯一的 JobException 类型", JobException.class, e.getClass());
            assertTrue(scenario + " 的消息不得为空", e.getMessage() != null && !e.getMessage().isEmpty());
            return e;
        }
    }

    /**
     * 断言消息含全部定位片段。
     *
     * @param e 启动期异常
     * @param fragments 期望出现在消息中的片段
     */
    private static void assertLocates(JobException e, String... fragments) {
        for (String fragment : fragments) {
            assertTrue("消息应含定位信息 " + fragment + "，实际：" + e.getMessage(),
                    e.getMessage().contains(fragment));
        }
        // 经 Throwable 视图断言：JobException 是 final 类，直接 instanceof 兄弟异常类型不可转换
        Throwable asThrowable = e;
        assertTrue("启动期异常应是 JobException", asThrowable instanceof JobException);
        assertTrue("启动期异常不得是 IllegalArgumentException",
                !(asThrowable instanceof IllegalArgumentException));
        assertTrue("启动期异常不得是 IllegalStateException",
                !(asThrowable instanceof IllegalStateException));
    }

    /**
     * 用扫描器扫一个候选类（表达式校验用替身）。
     *
     * <p>容器刻意不注册任何实例：本类断言的失败都在「取实例」之前发生（注解 / 签名 / 表达式），
     * 这样既不依赖测试类的可实例化性，也不会把「实例取不到」误当成本场景的失败原因。</p>
     *
     * @param type 候选类
     */
    private static void scan(Class<?> type) {
        new JobScanner(new TestJobContainer(), StubCronCalculator.accepting(VALID_CRON, 1600000000000L),
                Collections.singletonList(SCAN_PACKAGE)).scan(Collections.<Class<?>>singletonList(type));
    }

    /**
     * @param type 类
     * @param methodName 方法名
     * @return 期望的任务名（类全名#方法名）
     */
    private static String name(Class<?> type, String methodName) {
        return type.getName() + "#" + methodName;
    }

    /**
     * @param key 配置键名
     * @param value 配置值
     * @return 只含必填键与给定键值对的配置集合
     */
    private static Properties withProperty(String key, String value) {
        Properties props = props(JobConfig.SCAN_PACKAGES_KEY, "cn.acme.job");
        props.setProperty(key, value);
        return props;
    }

    /**
     * @param key 配置键名
     * @param value 配置值
     * @return 只含一个键值对的配置集合
     */
    private static Properties props(String key, String value) {
        Properties props = new Properties();
        props.setProperty(key, value);
        return props;
    }

    /**
     * @param taskName 任务名
     * @return 只用于注册表判重的任务定义
     */
    private static JobTask task(String taskName) {
        try {
            Method method = TaskBean.class.getDeclaredMethod("tick");
            return new JobTask(taskName, new TaskBean(), method, JobTrigger.fixedRate(1000L, 0L, taskName));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试用任务方法缺失", e);
        }
    }

    /**
     * 无参动作，便于把「触发失败」写成一行。
     */
    private interface Action {

        /**
         * 执行动作。
         */
        void run();
    }

    // ------------------------------------------------------------------ 测试用类型

    /**
     * 占位 Bean（只用于类型定位）。
     */
    private static final class TaskBean {

        /**
         * 任务方法。
         */
        public void tick() {
        }
    }

    /**
     * 监听器实现类（只用于类型定位）。
     */
    private static final class ListenerBean implements JobListener {

        @Override
        public void onEvent(JobEvent event) {
        }
    }

    /**
     * {@code static} 任务方法。
     */
    private static final class StaticJob {

        /**
         * 违规：static。
         */
        @FixedRate(period = 1000L)
        public static void bad() {
        }
    }

    /**
     * 有返回值的任务方法。
     */
    private static final class ReturnValueJob {

        /**
         * 违规：有返回值。
         *
         * @return 恒 0
         */
        @FixedRate(period = 1000L)
        public int bad() {
            return 0;
        }
    }

    /**
     * 参数非法的任务方法。
     */
    private static final class StringParameterJob {

        /**
         * 违规：参数既不是空也不是 JobEvent。
         *
         * @param ignored 参数
         */
        @FixedRate(period = 1000L)
        public void bad(String ignored) {
        }
    }

    /**
     * 同时声明两个触发注解的任务方法。
     */
    private static final class MultipleTriggerJob {

        /**
         * 违规：多触发注解。
         */
        @Cron(VALID_CRON)
        @FixedRate(period = 1000L)
        public void bad() {
        }
    }

    /**
     * cron 表达式非法的任务方法。
     */
    private static final class IllegalCronJob {

        /**
         * 违规：分钟越界。
         */
        @Cron(ILLEGAL_CRON)
        public void bad() {
        }
    }

    /**
     * 没有任何触发注解的类。
     */
    private static final class NoAnnotationJob {

        /**
         * 普通方法。
         */
        public void plain() {
        }
    }
}
