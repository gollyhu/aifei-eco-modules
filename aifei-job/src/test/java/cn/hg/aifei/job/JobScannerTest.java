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

import cn.hg.aifei.job.internal.JobRegistry;
import cn.hg.aifei.job.internal.JobScanner;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.internal.JobTrigger;
import cn.hg.aifei.job.support.StubCronCalculator;
import cn.hg.aifei.job.support.TestJobContainer;
import cn.hg.aifei.job.support.TestLogs;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 注解扫描与方法校验用例：{@code static} / 有返回值 / 非法参数 / 多触发注解 / 非法 cron / 扫不到任务 / 任务名判重。
 *
 * <p>扫描器只依赖两个内部契约（{@code JobContainer} / {@code CronCalculator}），本类用
 * {@code support.TestJobContainer} 与 {@code support.StubCronCalculator} 替身装配，<b>不连框架容器</b>；
 * 表达式语义本身由 {@code CronExpressionCasesTest} 独立覆盖。</p>
 *
 * <p>失败消息的定位口径一律是「任务名 = 类全名#方法名」，用例把期望值写成
 * {@code 类的全名 + "#" + 方法名}（由反射取类全名，<b>不经</b>被测的 {@code JobNames} 推导）。</p>
 */
public class JobScannerTest {

    /**
     * 本次扫描的包名，只用于「扫不到任务」时的消息断言。
     */
    private static final String SCAN_PACKAGE = "cn.hg.aifei.job.scanner";

    /**
     * 替身认定为合法的表达式。
     */
    private static final String VALID_CRON = "0 0/5 * * * ?";

    /**
     * 替身给合法表达式预置的下次触发时刻。
     */
    private static final long CRON_NEXT = 1600000004000L;

    /**
     * 替身认定为非法的表达式（分钟越界）。
     */
    private static final String ILLEGAL_CRON = "0 70 * * * ?";

    /**
     * 容器替身。
     */
    private TestJobContainer container;

    /**
     * cron 计算替身。
     */
    private StubCronCalculator cron;

    /**
     * 被测扫描器。
     */
    private JobScanner scanner;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    /**
     * 每例自建替身，保证用例独立。
     */
    @Before
    public void setUp() {
        cron = StubCronCalculator.accepting(VALID_CRON, CRON_NEXT);
        container = new TestJobContainer();
        scanner = new JobScanner(container, cron, Collections.singletonList(SCAN_PACKAGE));
    }

    // ------------------------------------------------------------------ 正常扫描

    /**
     * 合法扫描：两种触发方式各产出一个任务，任务名、触发参数与实例均符合契约。
     */
    @Test
    public void validScanProducesTasks() {
        ValidCronJob cronJob = new ValidCronJob();
        ValidFixedRateJob rateJob = new ValidFixedRateJob();
        container.register(ValidCronJob.class, cronJob).register(ValidFixedRateJob.class, rateJob);

        Map<String, JobTask> tasks = byName(scan(ValidCronJob.class, ValidFixedRateJob.class));

        assertEquals("应产出 2 个任务", 2, tasks.size());
        assertEquals("任务名应为「类全名#方法名」",
                new LinkedHashSet<>(Arrays.asList(
                        taskName(ValidCronJob.class, "tick"),
                        taskName(ValidFixedRateJob.class, "poll"))),
                tasks.keySet());

        JobTask cronTask = tasks.get(taskName(ValidCronJob.class, "tick"));
        assertEquals("cron 任务的触发方式应为 CRON", JobTrigger.Kind.CRON, cronTask.trigger().kind());
        assertEquals("cron 表达式应原样保留", VALID_CRON, cronTask.trigger().cron());
        assertSame("任务实例应来自容器", cronJob, cronTask.instance());
        assertTrue("扫描期应校验 cron 表达式，实际校验过的：" + cron.validatedExpressions(),
                cron.validatedExpressions().contains(VALID_CRON));

        JobTask rateTask = tasks.get(taskName(ValidFixedRateJob.class, "poll"));
        assertEquals("fixedRate 任务的触发方式应为 FIXED_RATE",
                JobTrigger.Kind.FIXED_RATE, rateTask.trigger().kind());
        assertEquals("period 应原样保留", 1500L, rateTask.trigger().period());
        assertEquals("initialDelay 应原样保留", 250L, rateTask.trigger().initialDelay());
        assertSame("任务实例应来自容器", rateJob, rateTask.instance());
    }

    // ------------------------------------------------------------------ 方法签名

    /**
     * {@code static} 方法不是任务方法。
     */
    @Test
    public void staticMethodFails() {
        assertSignatureFails(StaticJob.class, "bad");
    }

    /**
     * 有返回值的方法不是任务方法。
     */
    @Test
    public void methodWithReturnValueFails() {
        assertSignatureFails(ReturnValueJob.class, "bad");
    }

    /**
     * 参数既不是 {@code ()} 也不是 {@code (JobEvent)} 的方法不是任务方法。
     */
    @Test
    public void methodWithIllegalParameterFails() {
        assertSignatureFails(StringParameterJob.class, "bad");
    }

    /**
     * 非 {@code public} 方法不是任务方法。
     */
    @Test
    public void nonPublicMethodFails() {
        assertSignatureFails(NonPublicJob.class, "bad");
    }

    // ------------------------------------------------------------------ 注解与表达式

    /**
     * 同一方法声明多个触发注解：配置错误，启动即失败（消息含任务名与注解名）。
     */
    @Test
    public void multipleTriggerAnnotationsFail() {
        try {
            scan(MultipleTriggerJob.class);
            fail("同一方法多个触发注解应抛 JobException");
        } catch (JobException e) {
            String expected = taskName(MultipleTriggerJob.class, "bad");
            assertTrue("消息应含任务名定位 " + expected + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expected));
            assertTrue("消息应指出多触发注解，实际：" + e.getMessage(),
                    e.getMessage().contains("multiple trigger annotations"));
        }
    }

    /**
     * 非法 cron 表达式：启动即失败，消息含表达式<b>原文</b>与方法全名。
     */
    @Test
    public void illegalCronExpressionFailsWithExpressionLiteral() {
        try {
            scan(IllegalCronJob.class);
            fail("非法 cron 表达式应抛 JobException");
        } catch (JobException e) {
            String expected = taskName(IllegalCronJob.class, "bad");
            assertTrue("消息应含任务名定位 " + expected + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expected));
            assertTrue("消息应含表达式原文 " + ILLEGAL_CRON + "，实际：" + e.getMessage(),
                    e.getMessage().contains(ILLEGAL_CRON));
        }
    }

    /**
     * 触发注解属性越界（{@code @FixedRate} 不写 period，即默认值 0）：启动即失败，消息含属性名与方法全名。
     */
    @Test
    public void triggerParameterOutOfRangeFails() {
        try {
            scan(ZeroPeriodJob.class);
            fail("period 为 0 应抛 JobException");
        } catch (JobException e) {
            String expected = taskName(ZeroPeriodJob.class, "bad");
            assertTrue("消息应含任务名定位 " + expected + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expected));
            assertTrue("消息应含属性名 period，实际：" + e.getMessage(), e.getMessage().contains("period"));
        }
    }

    // ------------------------------------------------------------------ 扫不到 / 判重

    /**
     * 一个任务都没扫到：启动失败，消息列出扫描包。
     */
    @Test
    public void noTaskFoundFails() {
        try {
            scan(NoAnnotationJob.class);
            fail("扫不到任务应抛 JobException");
        } catch (JobException e) {
            assertTrue("消息应列出扫描包 " + SCAN_PACKAGE + "，实际：" + e.getMessage(),
                    e.getMessage().contains(SCAN_PACKAGE));
        }
    }

    /**
     * 继承而来的注解方法不注册（只扫类自身声明的方法），因此子类扫描结果为空。
     */
    @Test
    public void inheritedAnnotatedMethodIsNotRegistered() {
        try {
            scan(InheritedJob.class);
            fail("继承的注解方法不应被注册，应抛 noTaskFound");
        } catch (JobException e) {
            assertTrue("消息应列出扫描包 " + SCAN_PACKAGE + "，实际：" + e.getMessage(),
                    e.getMessage().contains(SCAN_PACKAGE));
        }
    }

    /**
     * 同一方法被扫到两次（扫描包存在包含关系）：由注册表按任务名判重失败，不静默去重。
     */
    @Test
    public void duplicateTaskNameFailsAtRegistry() {
        container.register(ValidFixedRateJob.class, new ValidFixedRateJob());
        List<JobTask> tasks = scan(ValidFixedRateJob.class, ValidFixedRateJob.class);
        assertEquals("重复扫描应产出两个同名任务", 2, tasks.size());
        try {
            new JobRegistry(tasks);
            fail("重复任务名应在注册表构造期抛 JobException");
        } catch (JobException e) {
            String expected = taskName(ValidFixedRateJob.class, "poll");
            assertTrue("消息应含重复的任务名 " + expected + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expected));
        }
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * 扫描给定候选类。
     *
     * @param types 候选类
     * @return 任务清单
     */
    private List<JobTask> scan(Class<?>... types) {
        return scanner.scan(Arrays.asList(types));
    }

    /**
     * 断言签名非法：{@code JobException} 且消息含「类全名#方法名」。
     *
     * @param type 候选类
     * @param methodName 违规方法名
     */
    private static void assertSignatureFails(Class<?> type, String methodName) {
        try {
            new JobScanner(new TestJobContainer(), StubCronCalculator.accepting(VALID_CRON, CRON_NEXT),
                    Collections.singletonList(SCAN_PACKAGE)).scan(Collections.<Class<?>>singletonList(type));
            fail("非法签名应抛 JobException：" + taskName(type, methodName));
        } catch (JobException e) {
            String expected = taskName(type, methodName);
            assertTrue("消息应含任务名定位 " + expected + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expected));
        }
    }

    /**
     * @param type 类
     * @param methodName 方法名
     * @return 期望的任务名（类全名#方法名）
     */
    private static String taskName(Class<?> type, String methodName) {
        return type.getName() + "#" + methodName;
    }

    /**
     * 按任务名索引任务清单。
     *
     * @param tasks 任务清单
     * @return 任务名到任务定义的映射
     */
    private static Map<String, JobTask> byName(List<JobTask> tasks) {
        Map<String, JobTask> map = new LinkedHashMap<>();
        for (JobTask task : tasks) {
            map.put(task.name(), task);
        }
        return map;
    }

    // ------------------------------------------------------------------ 测试用任务类

    /**
     * 合法 cron 任务（无参方法）。
     */
    private static final class ValidCronJob {

        /**
         * 合法任务方法。
         */
        @Cron(VALID_CRON)
        public void tick() {
        }

        /**
         * 无注解方法应被忽略。
         */
        public void helper() {
        }
    }

    /**
     * 合法固定频率任务（{@code (JobEvent)} 参数）。
     */
    private static final class ValidFixedRateJob {

        /**
         * 合法任务方法。
         *
         * @param event 本次执行的 STARTED 快照
         */
        @FixedRate(period = 1500L, initialDelay = 250L)
        public void poll(JobEvent event) {
        }

        /**
         * 无注解方法应被忽略。
         */
        public void helper() {
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
     * 非 {@code public} 的任务方法。
     */
    private static final class NonPublicJob {

        /**
         * 违规：非 public。
         */
        @FixedRate(period = 1000L)
        void bad() {
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
         * 违规：表达式分钟越界。
         */
        @Cron(ILLEGAL_CRON)
        public void bad() {
        }
    }

    /**
     * 未设置 period 的固定频率任务方法。
     */
    private static final class ZeroPeriodJob {

        /**
         * 违规：period 取默认值 0。
         */
        @FixedRate
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

    /**
     * 带注解方法的父类。
     */
    private static class BaseAnnotatedJob {

        /**
         * 父类声明的任务方法。
         */
        @FixedRate(period = 1000L)
        public void base() {
        }
    }

    /**
     * 自身未声明任务方法的子类（验证只扫自身声明的方法）。
     */
    private static final class InheritedJob extends BaseAnnotatedJob {
    }
}
