/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.job.local.QuartzCronSupport;
import cn.hg.aifei.job.support.TestLogs;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 表达式用例清单：合法 22 条 + 非法 9 条，共 31 条。
 *
 * <p><b>期望值纪律</b>：一律写死时刻字面量（{@code yyyy-MM-dd HH:mm:ss}），经固定时区
 * {@code Asia/Shanghai} 转 epoch 毫秒；用例内<b>不得</b>用被测实现或任何 {@code CronExpression} 调用推导期望值，
 * 否则断言恒真、锁不住语义。</p>
 *
 * <p>被测对象是表达式语义的唯一落点 {@link QuartzCronSupport}（本模块唯一使用 {@code org.quartz.*} 的类），
 * 故本类同时锁住「6 段含秒（可选第 7 段年）」与「不做 5 段兼容」两个口径。</p>
 */
public class CronExpressionCasesTest {

    /**
     * 用例统一使用的任务名，仅用于错误消息定位断言。
     */
    private static final String TASK_NAME = "com.acme.DemoJob#tick";

    /**
     * 时刻字面量的格式。
     */
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * 用例固定时区：期望值字面量都按东八区书写。
     */
    private static final String ZONE = "Asia/Shanghai";

    /**
     * 用例执行前的默认时区，{@code @AfterClass} 还原，避免污染同 JVM 内的其它用例。
     */
    private static TimeZone originalTimeZone;

    /**
     * 被测实现。
     */
    private static QuartzCronSupport cron;

    /**
     * 固定默认时区后再构造被测实现：{@code CronExpression} 的默认时区取自构造时刻的 JVM 默认时区。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
        cron = new QuartzCronSupport();
    }

    /**
     * 还原默认时区。
     */
    @AfterClass
    public static void afterClass() {
        TimeZone.setDefault(originalTimeZone);
    }

    // ------------------------------------------------------------------ 合法表达式（22 条）

    /**
     * 用例 1：步进分钟。
     */
    @Test
    public void stepMinuteReturnsNextMultipleOfFive() {
        assertNext("0 0/5 * * * ?", "2021-03-01 10:02:30", "2021-03-01 10:05:00");
    }

    /**
     * 用例 2：参考时刻正好落在触发点上时必须返回严格更晚的下一次。
     */
    @Test
    public void hitPointDoesNotReturnItself() {
        assertNext("0 0/5 * * * ?", "2021-03-01 10:05:00", "2021-03-01 10:10:00");
    }

    /**
     * 用例 3：秒级步进。
     */
    @Test
    public void stepSecondReturnsNextMultipleOfTen() {
        assertNext("*/10 * * * * ?", "2021-03-01 10:00:05", "2021-03-01 10:00:10");
    }

    /**
     * 用例 4：每日定点。
     */
    @Test
    public void dailyAtNoonFromBeforeThePoint() {
        assertNext("0 0 12 * * ?", "2021-03-01 11:59:59", "2021-03-01 12:00:00");
    }

    /**
     * 用例 5：命中当天的定点点后取次日。
     */
    @Test
    public void dailyAtNoonAfterHitPointTakesNextDay() {
        assertNext("0 0 12 * * ?", "2021-03-01 12:00:00", "2021-03-02 12:00:00");
    }

    /**
     * 用例 6：跨年。
     */
    @Test
    public void dailyAtNoonCrossesYearBoundary() {
        assertNext("0 0 12 * * ?", "2021-12-31 13:00:00", "2022-01-01 12:00:00");
    }

    /**
     * 用例 7：每年定点（第 7 段年字段为 {@code *}）。
     */
    @Test
    public void yearlyAtFixedDate() {
        assertNext("0 0 0 1 1 ? *", "2021-06-01 00:00:00", "2022-01-01 00:00:00");
    }

    /**
     * 用例 8：周字段用英文缩写（{@code SUN}）。
     */
    @Test
    public void weeklyFieldAcceptsDayName() {
        assertNext("0 30 4 ? * SUN", "2021-03-01 00:00:00", "2021-03-07 04:30:00");
    }

    /**
     * 用例 9：区间 + 工作日，周六跳过周日直接到周一。
     */
    @Test
    public void businessHoursRangeSkipsWeekend() {
        assertNext("0 0/30 9-17 ? * MON-FRI", "2021-03-06 10:00:00", "2021-03-08 09:00:00");
    }

    /**
     * 用例 10–12：月末 {@code L}（非闰 2 月 / 跨月 / 闰年 2 月）。
     */
    @Test
    public void lastDayOfMonthHandlesLeapAndNonLeapFebruary() {
        assertNext("0 0 0 L * ?", "2021-02-01 00:00:00", "2021-02-28 00:00:00");
        assertNext("0 0 0 L * ?", "2021-02-28 00:00:00", "2021-03-31 00:00:00");
        assertNext("0 0 0 L * ?", "2020-02-01 00:00:00", "2020-02-29 00:00:00");
    }

    /**
     * 用例 13：闰日跨年（下一个闰年是 2024）。
     */
    @Test
    public void leapDaySkipsNonLeapYears() {
        assertNext("0 0 0 29 2 ? *", "2021-03-01 00:00:00", "2024-02-29 00:00:00");
    }

    /**
     * 用例 14：闰日命中后取下一个闰年（2028）。
     */
    @Test
    public void leapDayTakesNextLeapYear() {
        assertNext("0 0 0 29 2 ? *", "2024-02-29 00:00:00", "2028-02-29 00:00:00");
    }

    /**
     * 用例 15–16：周字段上的 {@code L}（当月最后一个周五）与跨月。
     */
    @Test
    public void lastFridayOfMonthAndAcrossMonth() {
        assertNext("0 15 10 ? * 6L", "2021-03-01 00:00:00", "2021-03-26 10:15:00");
        assertNext("0 15 10 ? * 6L", "2021-03-26 10:15:00", "2021-04-30 10:15:00");
    }

    /**
     * 用例 17：{@code W} 落在周日 → 顺延到周一。
     */
    @Test
    public void nearestWeekdayMovesForwardFromSunday() {
        assertNext("0 0 12 15W * ?", "2021-08-01 00:00:00", "2021-08-16 12:00:00");
    }

    /**
     * 用例 18：{@code W} 落在周六 → 提前到周五。
     */
    @Test
    public void nearestWeekdayMovesBackwardFromSaturday() {
        assertNext("0 0 12 15W * ?", "2021-05-01 00:00:00", "2021-05-14 12:00:00");
    }

    /**
     * 用例 19：{@code #} 取当月第 3 个周五。
     */
    @Test
    public void thirdFridayOfMonth() {
        assertNext("0 0 12 ? * 6#3", "2021-03-01 00:00:00", "2021-03-19 12:00:00");
    }

    /**
     * 用例 20：{@code #} 取当月第 1 个周一（当日即命中，取当日定点）。
     */
    @Test
    public void firstMondayOfMonth() {
        assertNext("0 0 12 ? * 2#1", "2021-03-01 00:00:00", "2021-03-01 12:00:00");
    }

    /**
     * 用例 21：日字段列表。
     */
    @Test
    public void dayOfMonthList() {
        assertNext("0 0 0 1,15 * ?", "2021-03-10 00:00:00", "2021-03-15 00:00:00");
    }

    /**
     * 用例 22：第 7 段年字段（固定年份）。
     */
    @Test
    public void yearFieldIsHonored() {
        assertNext("0 0 0 1 6 ? 2030", "2021-03-01 00:00:00", "2030-06-01 00:00:00");
    }

    /**
     * 合法表达式校验不抛异常，且下次触发时刻严格晚于参考时刻。
     */
    @Test
    public void validExpressionYieldsStrictlyLaterPoint() {
        String expression = "0 0/5 * * * ?";
        cron.validate(expression, TASK_NAME);
        long from = at("2021-03-01 10:02:30");
        assertTrue("下次触发时刻必须严格晚于参考时刻", cron.nextAfter(expression, from, TASK_NAME) > from);
    }

    // ------------------------------------------------------------------ 非法表达式（9 条）

    /**
     * 非法 1：分钟越界（0–59）。
     */
    @Test
    public void minuteOutOfRangeIsRejected() {
        assertIllegal("0 70 * * * ?", true);
    }

    /**
     * 非法 2：小时越界（0–23）。
     */
    @Test
    public void hourOutOfRangeIsRejected() {
        assertIllegal("0 0 25 * * ?", true);
    }

    /**
     * 非法 3：日越界（1–31）。
     */
    @Test
    public void dayOfMonthOutOfRangeIsRejected() {
        assertIllegal("0 0 12 32 * ?", true);
    }

    /**
     * 非法 4：月越界（1–12）。
     */
    @Test
    public void monthOutOfRangeIsRejected() {
        assertIllegal("0 0 12 * 13 ?", true);
    }

    /**
     * 非法 5：周越界（1–7）。
     */
    @Test
    public void dayOfWeekOutOfRangeIsRejected() {
        assertIllegal("0 0 12 ? * 8", true);
    }

    /**
     * 非法 6：5 段写法（本模块不做 5 段兼容）。
     */
    @Test
    public void fiveFieldExpressionIsRejected() {
        assertIllegal("0 0 12 * *", true);
    }

    /**
     * 非法 7：{@code #} 值越界（1–5）。
     */
    @Test
    public void hashCountOutOfRangeIsRejected() {
        assertIllegal("0 0 12 ? * 6#6", true);
    }

    /**
     * 非法 8：不支持的字符。
     */
    @Test
    public void unsupportedCharacterIsRejected() {
        assertIllegal("0 0 12 * * $", true);
    }

    /**
     * 非法 9：空串与纯空白（无底层解析异常，{@code cause} 为 {@code null}）。
     */
    @Test
    public void emptyExpressionIsRejected() {
        assertIllegal("", false);
        assertIllegal("   ", false);
    }

    /**
     * 非法表达式在计算下次触发时同样失败（校验与计算是同一构造点）。
     */
    @Test
    public void illegalExpressionFailsOnNextAfterToo() {
        try {
            cron.nextAfter("0 70 * * * ?", at("2021-03-01 10:00:00"), TASK_NAME);
            fail("非法表达式计算下次触发时应抛 JobException");
        } catch (JobException e) {
            assertTrue("消息应含表达式原文，实际：" + e.getMessage(), e.getMessage().contains("0 70 * * * ?"));
            assertNotNull("底层解析异常应保留为 cause", e.getCause());
        }
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * 断言表达式在给定参考时刻之后的下次触发时刻等于期望字面量。
     *
     * @param expression 表达式原文
     * @param from 参考时刻字面量
     * @param expected 期望下次触发时刻字面量
     */
    private static void assertNext(String expression, String from, String expected) {
        assertEquals("表达式 [" + expression + "] 在 " + from + " 之后的下次触发时刻不符",
                at(expected), cron.nextAfter(expression, at(from), TASK_NAME));
    }

    /**
     * 断言表达式非法：抛 {@code JobException}，消息含任务名（定位）与表达式原文。
     *
     * @param expression 表达式原文
     * @param expectCause 是否期望保留底层解析异常作为 cause
     */
    private static void assertIllegal(String expression, boolean expectCause) {
        try {
            cron.validate(expression, TASK_NAME);
            fail("非法表达式应抛 JobException：" + expression);
        } catch (JobException e) {
            assertTrue("消息应含任务名定位 " + TASK_NAME + "，实际：" + e.getMessage(),
                    e.getMessage().contains(TASK_NAME));
            assertTrue("消息应含表达式原文 " + expression + "，实际：" + e.getMessage(),
                    e.getMessage().contains(expression));
            if (expectCause) {
                assertNotNull("底层解析异常应保留为 cause：" + expression, e.getCause());
            }
        }
    }

    /**
     * 把时刻字面量按固定时区转成 epoch 毫秒；字面量写错时用例直接失败。
     *
     * @param literal {@code yyyy-MM-dd HH:mm:ss} 形式的时刻
     * @return epoch 毫秒
     */
    private static long at(String literal) {
        SimpleDateFormat format = new SimpleDateFormat(PATTERN);
        format.setTimeZone(TimeZone.getTimeZone(ZONE));
        format.setLenient(false);
        try {
            return format.parse(literal).getTime();
        } catch (ParseException e) {
            throw new IllegalStateException("用例时刻字面量不合法：" + literal, e);
        }
    }
}
