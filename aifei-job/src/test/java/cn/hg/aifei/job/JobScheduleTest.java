/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import static org.junit.Assert.assertEquals;

import cn.hg.aifei.job.internal.JobSchedule;
import cn.hg.aifei.job.internal.JobTask;
import cn.hg.aifei.job.internal.JobTrigger;
import cn.hg.aifei.job.support.StubCronCalculator;
import cn.hg.aifei.job.support.TestLogs;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 节拍纯函数用例（{@code JobSchedule#nextDelay} / {@code JobSchedule#overduePlanTime}）。
 *
 * <p>两个方法都是纯函数、<b>不读时钟</b>，因此本类全部为纯计算断言：时刻一律由用例以字面量给出，
 * 期望值也是字面量，不做任何时间等待、不依赖执行速度。</p>
 *
 * <p>cron 分支用 {@code support.StubCronCalculator} 预置「下次触发时刻」，从而把「表达式语义」排除在外
 * （那部分由 {@code CronExpressionCasesTest} 覆盖），只验证分叉与基准选取。</p>
 */
public class JobScheduleTest {

    /**
     * 任务名（类全名#方法名）字面量，仅用于装配与错误消息。
     */
    private static final String NAME = "com.acme.OrderJob#clean";

    /**
     * 统一的节拍计算基准时刻字面量。
     */
    private static final long NOW = 1600000000000L;

    /**
     * 替身给表达式 {@link #CRON_STEP} 预置的下次触发时刻（即 {@code NOW + 4000}）。
     */
    private static final long CRON_NEXT = 1600000004000L;

    /**
     * 替身给表达式 {@link #CRON_YEARLY} 预置的下次触发时刻（即 {@code NOW + 2500}）。
     */
    private static final long CRON_YEARLY_NEXT = 1600000002500L;

    /**
     * 预置了下次触发时刻的表达式：5 分钟步进。
     */
    private static final String CRON_STEP = "0 0/5 * * * ?";

    /**
     * 预置了下次触发时刻的表达式：每年定点。
     */
    private static final String CRON_YEARLY = "0 0 0 1 1 ? *";

    /**
     * 预置为「永不再触发」的表达式。
     */
    private static final String CRON_EXHAUSTED = "0 0 0 29 2 ? 2099";

    /**
     * 任务方法：纯计算用例不调用任务体，只要求是真实 {@link Method}。
     */
    private static final Method TICK = tickMethod();

    /**
     * 被测纯函数所在实例。
     */
    private JobSchedule schedule;

    /**
     * 装配空日志工厂。
     */
    @BeforeClass
    public static void beforeClass() {
        TestLogs.install();
    }

    /**
     * 每个用例自建替身与纯函数实例，保证用例独立、幂等。
     */
    @Before
    public void setUp() {
        Map<String, Long> nextTimes = new LinkedHashMap<>();
        nextTimes.put(CRON_STEP, CRON_NEXT);
        nextTimes.put(CRON_YEARLY, CRON_YEARLY_NEXT);
        nextTimes.put(CRON_EXHAUSTED, JobSchedule.NONE);
        schedule = new JobSchedule(new StubCronCalculator(nextTimes));
    }

    // ------------------------------------------------------------------ nextDelay：首次分支

    /**
     * 首次：固定频率只返回 {@code initialDelay}，与间隔无关。
     */
    @Test
    public void firstDelayOfFixedRateIsInitialDelay() {
        JobTask task = task(JobTrigger.fixedRate(1000L, 300L, NAME));
        assertEquals("首次排期应返回 initialDelay", 300L, schedule.nextDelay(task, 0L, 0L, NOW, true));
    }

    /**
     * 首次：固定延迟只返回 {@code initialDelay}，与延迟无关。
     */
    @Test
    public void firstDelayOfFixedDelayIsInitialDelay() {
        JobTask task = task(JobTrigger.fixedDelay(2000L, 700L, NAME));
        assertEquals("首次排期应返回 initialDelay", 700L, schedule.nextDelay(task, 0L, 0L, NOW, true));
    }

    /**
     * 首次：cron 取表达式在基准时刻之后的下一个时刻（与 lastStart / lastEnd 无关）。
     */
    @Test
    public void firstDelayOfCronIsGapToNextPoint() {
        JobTask task = task(JobTrigger.cron(CRON_STEP, NAME));
        assertEquals("首次排期应返回「下次触发时刻 - 基准时刻」",
                4000L, schedule.nextDelay(task, 0L, 0L, NOW, true));
    }

    // ------------------------------------------------------------------ nextDelay：非首次分支

    /**
     * 非首次：固定频率以「上次<b>开始</b> + 间隔」为下次点（不是上次结束）。
     */
    @Test
    public void nextDelayOfFixedRateIsBasedOnLastStart() {
        JobTask task = task(JobTrigger.fixedRate(1000L, 0L, NAME));
        assertEquals("fixedRate 应以 lastStart 为基准：lastStart + period - now",
                600L, schedule.nextDelay(task, 1599999999600L, 1599999999100L, NOW, false));
    }

    /**
     * 非首次：固定延迟以「上次<b>结束</b> + 延迟」为下次点（不是上次开始）。
     */
    @Test
    public void nextDelayOfFixedDelayIsBasedOnLastEnd() {
        JobTask task = task(JobTrigger.fixedDelay(1000L, 0L, NAME));
        assertEquals("fixedDelay 应以 lastEnd 为基准：lastEnd + delay - now",
                700L, schedule.nextDelay(task, 1599999999100L, 1599999999700L, NOW, false));
    }

    /**
     * 非首次：cron 取表达式在基准时刻之后的下一个时刻，完全忽略 lastStart / lastEnd。
     */
    @Test
    public void nextDelayOfCronIgnoresLastStartAndLastEnd() {
        JobTask task = task(JobTrigger.cron(CRON_YEARLY, NAME));
        assertEquals("cron 非首次应返回「下次触发时刻 - 基准时刻」",
                2500L, schedule.nextDelay(task, 1L, 2L, NOW, false));
    }

    /**
     * {@code isFirst = false} 时 {@code initialDelay} 被完全忽略：把它改成任意大，结果与 0 时完全一致。
     *
     * <p>这是「被拒重排不重复消费 initialDelay」的纯计算证据。</p>
     */
    @Test
    public void initialDelayIsIgnoredWhenNotFirst() {
        long baseline = schedule.nextDelay(
                task(JobTrigger.fixedRate(1000L, 0L, NAME)), NOW, NOW, NOW, false);
        long withHugeInitialDelay = schedule.nextDelay(
                task(JobTrigger.fixedRate(1000L, 999999L, NAME)), NOW, NOW, NOW, false);
        assertEquals("非首次结果应等于 period（上次开始即基准时刻）", 1000L, baseline);
        assertEquals("非首次时 initialDelay 应完全不参与计算", baseline, withHugeInitialDelay);
    }

    /**
     * 被拒重排口径（A19）：同一组入参下 {@code isFirst=true} 与 {@code false} 的差异只来自首次分支。
     */
    @Test
    public void firstAndNonFirstDifferOnlyInFirstBranch() {
        JobTask task = task(JobTrigger.fixedRate(1000L, 0L, NAME));
        assertEquals("isFirst=true 时返回 initialDelay（此处为 0）",
                0L, schedule.nextDelay(task, NOW, NOW, NOW, true));
        assertEquals("isFirst=false 时返回 period",
                1000L, schedule.nextDelay(task, NOW, NOW, NOW, false));
    }

    /**
     * 下次点已过去（任务耗时超过间隔）时返回 0，表示立即重排：已过的触发点不补跑。
     */
    @Test
    public void nextDelayIsZeroWhenPlanPointAlreadyPassed() {
        JobTask task = task(JobTrigger.fixedRate(1000L, 0L, NAME));
        assertEquals("下次点已过去应返回 0（不补跑、不返回负值）",
                0L, schedule.nextDelay(task, 1599999995000L, 1599999999000L, NOW, false));
    }

    /**
     * 下次点恰好等于基准时刻时返回 0。
     */
    @Test
    public void nextDelayIsZeroWhenPlanPointEqualsNow() {
        JobTask task = task(JobTrigger.fixedRate(1000L, 0L, NAME));
        assertEquals("下次点等于基准时刻应返回 0", 0L, schedule.nextDelay(task, 1599999999000L, NOW, NOW, false));
    }

    /**
     * cron 无下次触发时刻时返回 {@link JobSchedule#NONE}（首次与非首次一致）。
     */
    @Test
    public void nextDelayReturnsNoneWhenCronHasNoNextPoint() {
        JobTask task = task(JobTrigger.cron(CRON_EXHAUSTED, NAME));
        assertEquals("cron 无下次触发时非首次应返回 NONE",
                JobSchedule.NONE, schedule.nextDelay(task, NOW, NOW, NOW, false));
        assertEquals("cron 无下次触发时首次应返回 NONE",
                JobSchedule.NONE, schedule.nextDelay(task, 0L, 0L, NOW, true));
    }

    /**
     * {@code nextDelay} 不读时钟：同一入参重复调用结果恒等；基准时刻后移多少，结果就增加多少
     * （下次点只由入参决定，与「现在几点」无关）。
     */
    @Test
    public void nextDelayDoesNotReadClock() {
        JobTask rate = task(JobTrigger.fixedRate(1000L, 0L, NAME));
        long first = schedule.nextDelay(rate, 1599999999600L, 1599999999100L, NOW, false);
        assertEquals("同一入参重复调用结果应恒等", first,
                schedule.nextDelay(rate, 1599999999600L, 1599999999100L, NOW, false));
        assertEquals("基准时刻提前 1000ms，结果应同步增加 1000ms", first + 1000L,
                schedule.nextDelay(rate, 1599999999600L, 1599999999100L, NOW - 1000L, false));

        JobTask cronTask = task(JobTrigger.cron(CRON_STEP, NAME));
        long cronFirst = schedule.nextDelay(cronTask, 0L, 0L, NOW, false);
        assertEquals("cron 同一入参重复调用结果应恒等", cronFirst,
                schedule.nextDelay(cronTask, 0L, 0L, NOW, false));
        assertEquals("cron 下次点固定，基准时刻提前 1000ms 结果应同步增加 1000ms", cronFirst + 1000L,
                schedule.nextDelay(cronTask, 0L, 0L, NOW - 1000L, false));
    }

    // ------------------------------------------------------------------ overduePlanTime

    /**
     * 计划点已到：返回<b>原计划触发时刻</b>（{@code lastStart + period}），不是当前时刻。
     */
    @Test
    public void overduePlanTimeReturnsPlanPoint() {
        JobTask task = task(JobTrigger.fixedRate(2500L, 0L, NAME));
        assertEquals("超时跳过的事件时间应为原计划触发时刻 lastStart + period",
                1600000002500L, schedule.overduePlanTime(task, 1600000000000L, 1600000010000L));
    }

    /**
     * 计划点恰好等于基准时刻：判为超时（事件时间不晚于基准时刻）。
     */
    @Test
    public void overduePlanTimeIncludesPlanPointEqualToNow() {
        JobTask task = task(JobTrigger.fixedRate(2500L, 0L, NAME));
        assertEquals("计划点等于基准时刻应判为超时",
                1600000002500L, schedule.overduePlanTime(task, 1600000000000L, 1600000002500L));
    }

    /**
     * 计划点尚未到：不构成超时，返回 {@link JobSchedule#NONE}。
     */
    @Test
    public void overduePlanTimeReturnsNoneWhenNotYetDue() {
        JobTask task = task(JobTrigger.fixedRate(2500L, 0L, NAME));
        assertEquals("计划点尚未到不应产生跳过事件",
                JobSchedule.NONE, schedule.overduePlanTime(task, 1600000000000L, 1600000002000L));
    }

    /**
     * 固定延迟与 cron 不产生此类跳过。
     */
    @Test
    public void overduePlanTimeReturnsNoneForNonFixedRateTriggers() {
        JobTask delayTask = task(JobTrigger.fixedDelay(2500L, 0L, NAME));
        assertEquals("fixedDelay 不产生超时跳过", JobSchedule.NONE,
                schedule.overduePlanTime(delayTask, 1600000000000L, 1600000010000L));
        JobTask cronTask = task(JobTrigger.cron(CRON_STEP, NAME));
        assertEquals("cron 不产生超时跳过", JobSchedule.NONE,
                schedule.overduePlanTime(cronTask, 1600000000000L, 1600000010000L));
    }

    /**
     * {@code overduePlanTime} 不读时钟：重复调用恒等，且移动基准时刻不改变返回的计划点（只改变判定结果）。
     */
    @Test
    public void overduePlanTimeDoesNotReadClock() {
        JobTask task = task(JobTrigger.fixedRate(2500L, 0L, NAME));
        assertEquals("同一入参重复调用结果应恒等",
                1600000002500L, schedule.overduePlanTime(task, 1600000000000L, 1600000010000L));
        assertEquals("计划点由 lastStart 与 period 决定，与基准时刻无关",
                1600000002500L, schedule.overduePlanTime(task, 1600000000000L, 1600000099999L));
    }

    /**
     * 「无下次触发」与「无跳过」共用同一个哨兵值 {@link JobSchedule#NONE}（= -1）。
     */
    @Test
    public void noneSentinelIsMinusOne() {
        assertEquals("NONE 哨兵值应为 -1", -1L, JobSchedule.NONE);
    }

    // ------------------------------------------------------------------ 辅助方法

    /**
     * 装配任务定义；纯计算用例不调用实例与方法的执行语义。
     *
     * @param trigger 触发方式
     * @return 任务定义
     */
    private static JobTask task(JobTrigger trigger) {
        return new JobTask(NAME, new Probe(), TICK, trigger);
    }

    /**
     * @return 供任务定义引用的真实方法
     */
    private static Method tickMethod() {
        try {
            return Probe.class.getDeclaredMethod("tick");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("测试方法缺失", e);
        }
    }

    /**
     * 任务方法载体，方法体不被调用。
     */
    private static final class Probe {

        /**
         * 任务方法占位。
         */
        void tick() {
        }
    }
}
