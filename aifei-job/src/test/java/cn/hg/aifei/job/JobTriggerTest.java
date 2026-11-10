/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.internal.JobTrigger;
import cn.hg.aifei.job.support.TestLogs;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 触发注解属性校验用例（{@code internal.JobTrigger} 的三个静态工厂）。
 *
 * <p>规则：{@code period} / {@code delay} 必须 {@code >= 1}（注解默认值 {@code 0} 视为<b>未设置</b>），
 * {@code initialDelay} 必须 {@code >= 0}；越界一律 {@link JobException}（<b>不是</b>
 * {@code IllegalArgumentException} / {@code IllegalStateException}），消息必须同时含
 * <b>任务名</b>（类全名#方法名）与<b>原值</b>，便于定位写错的那一行注解。</p>
 */
public class JobTriggerTest {

    /**
     * 用例统一使用的任务名，用于消息定位断言。
     */
    private static final String TASK = "com.acme.OrderJob#clean";

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    // ------------------------------------------------------------------ 未设置（默认值 0）

    /**
     * {@code @FixedRate} 不写 {@code period}：默认值 0 视为未设置，启动失败。
     */
    @Test
    public void fixedRateWithoutPeriodFails() {
        assertFixedRateFails(0L, 0L, "period", "0");
    }

    /**
     * {@code @FixedDelay} 不写 {@code delay}：默认值 0 视为未设置，启动失败。
     */
    @Test
    public void fixedDelayWithoutDelayFails() {
        assertFixedDelayFails(0L, 0L, "delay", "0");
    }

    // ------------------------------------------------------------------ 负数

    /**
     * {@code period} 为负：启动失败，消息含属性名与原值。
     */
    @Test
    public void negativePeriodFails() {
        assertFixedRateFails(-5L, 0L, "period", "-5");
    }

    /**
     * {@code delay} 为负：启动失败，消息含属性名与原值。
     */
    @Test
    public void negativeDelayFails() {
        assertFixedDelayFails(-7L, 0L, "delay", "-7");
    }

    /**
     * {@code initialDelay} 为负：固定频率与固定延迟两条路径都失败，消息含属性名与原值。
     */
    @Test
    public void negativeInitialDelayFails() {
        assertFixedRateFails(1000L, -1L, "initialDelay", "-1");
        assertFixedDelayFails(1000L, -3600L, "initialDelay", "-3600");
    }

    // ------------------------------------------------------------------ cron 表达式

    /**
     * cron 表达式为 {@code null}、空串或纯空白：启动失败，消息含任务名。
     */
    @Test
    public void blankCronExpressionFails() {
        assertCronFails(null);
        assertCronFails("");
        assertCronFails("   ");
    }

    /**
     * 空白表达式是「未设置」而不是「解析失败」：本类不做解析，故只锁定消息定位口径。
     */
    @Test
    public void blankCronMessageLocatesTask() {
        try {
            JobTrigger.cron("   ", TASK);
            fail("空白表达式应抛 JobException");
        } catch (JobException e) {
            assertTrue("消息应含任务名 " + TASK + "，实际：" + e.getMessage(), e.getMessage().contains(TASK));
            assertTrue("消息应含表达式原文，实际：" + e.getMessage(), e.getMessage().contains("   "));
        }
    }

    // ------------------------------------------------------------------ 合法取值

    /**
     * 合法固定频率：{@code kind} / {@code period} / {@code initialDelay} 原样保留，
     * 其余触发方式的字段为中性值（{@code cron == null}、{@code delay == 0}）。
     */
    @Test
    public void validFixedRateKeepsValues() {
        JobTrigger trigger = JobTrigger.fixedRate(1500L, 250L, TASK);
        assertEquals("触发方式应为 FIXED_RATE", JobTrigger.Kind.FIXED_RATE, trigger.kind());
        assertEquals("period 应原样保留", 1500L, trigger.period());
        assertEquals("initialDelay 应原样保留", 250L, trigger.initialDelay());
        assertNull("固定频率不应持有表达式", trigger.cron());
        assertEquals("非固定延迟的 delay 应为 0", 0L, trigger.delay());
    }

    /**
     * 合法固定延迟：{@code kind} / {@code delay} / {@code initialDelay} 原样保留，
     * {@code period} 为中性值 {@code 0}。
     */
    @Test
    public void validFixedDelayKeepsValues() {
        JobTrigger trigger = JobTrigger.fixedDelay(800L, 100L, TASK);
        assertEquals("触发方式应为 FIXED_DELAY", JobTrigger.Kind.FIXED_DELAY, trigger.kind());
        assertEquals("delay 应原样保留", 800L, trigger.delay());
        assertEquals("initialDelay 应原样保留", 100L, trigger.initialDelay());
        assertNull("固定延迟不应持有表达式", trigger.cron());
        assertEquals("非固定频率的 period 应为 0", 0L, trigger.period());
    }

    /**
     * 合法 cron：表达式原样保留（两侧空白被去掉），三个毫秒属性一律为 {@code 0}。
     */
    @Test
    public void validCronKeepsExpression() {
        JobTrigger trigger = JobTrigger.cron(" 0 0/5 * * * ? ", TASK);
        assertEquals("触发方式应为 CRON", JobTrigger.Kind.CRON, trigger.kind());
        assertEquals("表达式应去两侧空白后原样保留", "0 0/5 * * * ?", trigger.cron());
        assertEquals("cron 无 initialDelay", 0L, trigger.initialDelay());
        assertEquals("cron 无 period", 0L, trigger.period());
        assertEquals("cron 无 delay", 0L, trigger.delay());
    }

    /**
     * 边界下限可取：{@code period = 1}、{@code delay = 1}、{@code initialDelay = 0}。
     */
    @Test
    public void lowerBoundsAreAccepted() {
        assertEquals("period = 1 应被接受", 1L, JobTrigger.fixedRate(1L, 0L, TASK).period());
        assertEquals("delay = 1 应被接受", 1L, JobTrigger.fixedDelay(1L, 0L, TASK).delay());
        assertEquals("initialDelay = 0 应被接受", 0L, JobTrigger.fixedRate(1000L, 0L, TASK).initialDelay());
        assertEquals("initialDelay = 0 应被接受", 0L, JobTrigger.fixedDelay(1000L, 0L, TASK).initialDelay());
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * 断言固定频率工厂失败：{@link JobException} + 任务名 + 属性名 + 原值。
     *
     * @param period 间隔
     * @param initialDelay 首次延迟
     * @param parameter 期望出现在消息中的属性名
     * @param value 期望出现在消息中的原值
     */
    private static void assertFixedRateFails(long period, long initialDelay, String parameter, String value) {
        try {
            JobTrigger.fixedRate(period, initialDelay, TASK);
            fail("period=" + period + ", initialDelay=" + initialDelay + " 应抛 JobException");
        } catch (JobException e) {
            assertLocates(e, parameter, value);
        }
    }

    /**
     * 断言固定延迟工厂失败：{@link JobException} + 任务名 + 属性名 + 原值。
     *
     * @param delay 延迟
     * @param initialDelay 首次延迟
     * @param parameter 期望出现在消息中的属性名
     * @param value 期望出现在消息中的原值
     */
    private static void assertFixedDelayFails(long delay, long initialDelay, String parameter, String value) {
        try {
            JobTrigger.fixedDelay(delay, initialDelay, TASK);
            fail("delay=" + delay + ", initialDelay=" + initialDelay + " 应抛 JobException");
        } catch (JobException e) {
            assertLocates(e, parameter, value);
        }
    }

    /**
     * 断言 cron 工厂对给定表达式失败。
     *
     * @param expression 表达式
     */
    private static void assertCronFails(String expression) {
        try {
            JobTrigger.cron(expression, TASK);
            fail("表达式 " + expression + " 应抛 JobException");
        } catch (JobException e) {
            assertTrue("消息应含任务名 " + TASK + "，实际：" + e.getMessage(), e.getMessage().contains(TASK));
        }
    }

    /**
     * 断言异常携带定位信息，且不是其它异常类型的伪装。
     *
     * @param e 异常
     * @param parameter 期望出现在消息中的属性名
     * @param value 期望出现在消息中的原值
     */
    private static void assertLocates(JobException e, String parameter, String value) {
        // 经 Throwable 视图断言：JobException 是 final 类，直接 instanceof 兄弟异常类型不可转换
        Throwable asThrowable = e;
        assertFalse("启动期异常不得是 IllegalArgumentException",
                asThrowable instanceof IllegalArgumentException);
        assertFalse("启动期异常不得是 IllegalStateException", asThrowable instanceof IllegalStateException);
        assertTrue("消息应含任务名 " + TASK + "，实际：" + e.getMessage(), e.getMessage().contains(TASK));
        assertTrue("消息应含属性名 " + parameter + "，实际：" + e.getMessage(),
                e.getMessage().contains(parameter));
        assertTrue("消息应含原值 " + value + "，实际：" + e.getMessage(), e.getMessage().contains(value));
    }
}
