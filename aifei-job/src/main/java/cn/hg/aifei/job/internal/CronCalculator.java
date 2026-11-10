/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

/**
 * cron 计算契约：把 Quartz 类型挡在 {@code internal} 之外，实现见 {@code local.QuartzCronSupport}。
 *
 * <p>两个方法都以「任务名」（类全名#方法名）与「表达式原文」作为定位信息，实现方在失败时须把这两项写进
 * 启动期异常消息，业务方据此定位到具体方法。表达式语义一律为 Quartz 语义（6 段含秒，可选第 7 段年），
 * 本模块不做 5 段兼容写法。</p>
 */
public interface CronCalculator {

    /**
     * 无下次触发时刻的返回值。
     */
    long NO_NEXT = -1L;

    /**
     * 校验表达式；非法即抛启动期异常。
     *
     * @param expression 表达式原文
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @throws cn.hg.aifei.job.JobException 表达式为 {@code null}、空白或无法解析
     */
    void validate(String expression, String taskName);

    /**
     * 计算 {@code epochMillis} 之后（严格晚于该时刻）的第一个触发时刻。
     *
     * @param expression 表达式原文
     * @param epochMillis 基准时刻，epoch 毫秒
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 下次触发时刻，epoch 毫秒；表达式永不再触发时返回 {@link #NO_NEXT}
     * @throws cn.hg.aifei.job.JobException 表达式为 {@code null}、空白或无法解析
     */
    long nextAfter(String expression, long epochMillis, String taskName);
}
