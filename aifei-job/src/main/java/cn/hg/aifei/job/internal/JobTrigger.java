/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

/**
 * 触发方式的不可变值对象，同时是触发注解属性校验的唯一定义点。
 *
 * <p>三种触发方式语义单义：{@link Kind#CRON} 只持有表达式，{@link Kind#FIXED_RATE} 只持有间隔，
 * {@link Kind#FIXED_DELAY} 只持有延迟；{@code initialDelay} 只对固定频率 / 固定延迟有意义，且只影响首次触发。
 * 属性越界在静态工厂内即失败（经 {@link JobErrors} 抛 {@link cn.hg.aifei.job.JobException}），
 * 表达式本身是否合法由 cron 计算契约校验，不在本类。</p>
 */
public final class JobTrigger {

    /**
     * 触发方式。
     */
    public enum Kind {

        /**
         * 按 cron 表达式触发。
         */
        CRON,

        /**
         * 以上次开始执行为基准的固定频率触发。
         */
        FIXED_RATE,

        /**
         * 以上次结束为基准的固定延迟触发。
         */
        FIXED_DELAY
    }

    /**
     * 注解属性名常量：固定频率的间隔。只在错误消息里使用。
     */
    private static final String TASK_PARAM_PERIOD = "period";

    /**
     * 注解属性名常量：固定延迟的延迟。只在错误消息里使用。
     */
    private static final String TASK_PARAM_DELAY = "delay";

    /**
     * 注解属性名常量：首次触发延迟。只在错误消息里使用。
     */
    private static final String TASK_PARAM_INITIAL_DELAY = "initialDelay";

    /**
     * 触发方式。
     */
    private final Kind kind;

    /**
     * cron 表达式，仅 {@link Kind#CRON} 非 {@code null}。
     */
    private final String cron;

    /**
     * 固定频率的间隔，单位毫秒；非 {@link Kind#FIXED_RATE} 为 {@code 0}。
     */
    private final long period;

    /**
     * 固定延迟的延迟，单位毫秒；非 {@link Kind#FIXED_DELAY} 为 {@code 0}。
     */
    private final long delay;

    /**
     * 首次触发延迟，单位毫秒；{@link Kind#CRON} 恒为 {@code 0}。
     */
    private final long initialDelay;

    /**
     * 禁止外部构造，只能经三个静态工厂创建。
     *
     * @param kind 触发方式
     * @param cron cron 表达式
     * @param period 固定频率的间隔，单位毫秒
     * @param delay 固定延迟的延迟，单位毫秒
     * @param initialDelay 首次触发延迟，单位毫秒
     */
    private JobTrigger(Kind kind, String cron, long period, long delay, long initialDelay) {
        this.kind = kind;
        this.cron = cron;
        this.period = period;
        this.delay = delay;
        this.initialDelay = initialDelay;
    }

    /**
     * 构造 cron 触发方式，并校验表达式非空白。
     *
     * @param expression cron 表达式原文（两侧空白会被去掉）
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 触发方式值对象
     * @throws cn.hg.aifei.job.JobException 表达式为 {@code null} 或空白
     */
    public static JobTrigger cron(String expression, String taskName) {
        if (expression == null || expression.trim().isEmpty()) {
            throw JobErrors.illegalCron(taskName, expression, null);
        }
        return new JobTrigger(Kind.CRON, expression.trim(), 0L, 0L, 0L);
    }

    /**
     * 构造固定频率触发方式，并校验间隔与首次延迟。
     *
     * @param period 间隔，单位毫秒，必须 {@code >= 1}
     * @param initialDelay 首次触发延迟，单位毫秒，必须 {@code >= 0}
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 触发方式值对象
     * @throws cn.hg.aifei.job.JobException 间隔小于 {@code 1} 或首次延迟为负
     */
    public static JobTrigger fixedRate(long period, long initialDelay, String taskName) {
        requirePositive(TASK_PARAM_PERIOD, period, taskName);
        requireNonNegative(TASK_PARAM_INITIAL_DELAY, initialDelay, taskName);
        return new JobTrigger(Kind.FIXED_RATE, null, period, 0L, initialDelay);
    }

    /**
     * 构造固定延迟触发方式，并校验延迟与首次延迟。
     *
     * @param delay 延迟，单位毫秒，必须 {@code >= 1}
     * @param initialDelay 首次触发延迟，单位毫秒，必须 {@code >= 0}
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 触发方式值对象
     * @throws cn.hg.aifei.job.JobException 延迟小于 {@code 1} 或首次延迟为负
     */
    public static JobTrigger fixedDelay(long delay, long initialDelay, String taskName) {
        requirePositive(TASK_PARAM_DELAY, delay, taskName);
        requireNonNegative(TASK_PARAM_INITIAL_DELAY, initialDelay, taskName);
        return new JobTrigger(Kind.FIXED_DELAY, null, 0L, delay, initialDelay);
    }

    /**
     * @return 触发方式
     */
    public Kind kind() {
        return kind;
    }

    /**
     * @return cron 表达式；非 {@link Kind#CRON} 返回 {@code null}
     */
    public String cron() {
        return cron;
    }

    /**
     * @return 固定频率的间隔，单位毫秒；非 {@link Kind#FIXED_RATE} 返回 {@code 0}
     */
    public long period() {
        return period;
    }

    /**
     * @return 固定延迟的延迟，单位毫秒；非 {@link Kind#FIXED_DELAY} 返回 {@code 0}
     */
    public long delay() {
        return delay;
    }

    /**
     * @return 首次触发延迟，单位毫秒；{@link Kind#CRON} 返回 {@code 0}
     */
    public long initialDelay() {
        return initialDelay;
    }

    /**
     * 校验取值 {@code >= 1}。
     *
     * @param parameter 注解属性名
     * @param value 属性取值
     * @param taskName 任务名（类全名#方法名）
     */
    private static void requirePositive(String parameter, long value, String taskName) {
        if (value < 1L) {
            throw JobErrors.illegalParameter(taskName, parameter, value, ">= 1 (milliseconds)");
        }
    }

    /**
     * 校验取值 {@code >= 0}。
     *
     * @param parameter 注解属性名
     * @param value 属性取值
     * @param taskName 任务名（类全名#方法名）
     */
    private static void requireNonNegative(String parameter, long value, String taskName) {
        if (value < 0L) {
            throw JobErrors.illegalParameter(taskName, parameter, value, ">= 0 (milliseconds)");
        }
    }
}
