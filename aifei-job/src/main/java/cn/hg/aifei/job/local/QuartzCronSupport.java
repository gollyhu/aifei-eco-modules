/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.local;

import cn.hg.aifei.job.internal.CronCalculator;
import cn.hg.aifei.job.internal.JobErrors;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.quartz.CronExpression;

/**
 * cron 计算的唯一实现，也是<b>全模块唯一出现 {@code org.quartz.*} 的地方</b>：校验即构造
 * {@link CronExpression}，下次触发即 {@link CronExpression#getNextValidTimeAfter(Date)}。
 *
 * <p>Quartz 只被当作「表达式解析器 + 时刻计算器」使用，不启用它的调度器、触发器、任务存储与 misfire
 * 策略；调度内核是 JDK 自续排（见设计决策 1）。</p>
 *
 * <p>{@link CronExpression} 的构造有一次解析开销，故按表达式原文缓存在 {@link ConcurrentHashMap} 中：
 * 命中缓存后只调用 {@code getNextValidTimeAfter}，该调用只读、可被多个执行线程并发共用同一实例。
 * 解析失败不写缓存，异常原样保留为 {@code cause}，供启动期失败定位。</p>
 */
public final class QuartzCronSupport implements CronCalculator {

    /**
     * 表达式原文到已解析对象的缓存，只增不改。
     */
    private final Map<String, CronExpression> cache = new ConcurrentHashMap<>();

    /**
     * 校验表达式：能构造出 {@link CronExpression} 即合法。
     *
     * @param expression 表达式原文
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @throws cn.hg.aifei.job.JobException 表达式为 {@code null}、空白或无法解析
     */
    @Override
    public void validate(String expression, String taskName) {
        compile(expression, taskName);
    }

    /**
     * 计算基准时刻之后（严格晚于该时刻）的第一个触发时刻。
     *
     * @param expression 表达式原文
     * @param epochMillis 基准时刻，epoch 毫秒
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 下次触发时刻，epoch 毫秒；表达式永不再触发时返回 {@link CronCalculator#NO_NEXT}
     * @throws cn.hg.aifei.job.JobException 表达式为 {@code null}、空白或无法解析
     */
    @Override
    public long nextAfter(String expression, long epochMillis, String taskName) {
        Date next = compile(expression, taskName).getNextValidTimeAfter(new Date(epochMillis));
        return next == null ? NO_NEXT : next.getTime();
    }

    /**
     * 取已解析对象：命中缓存直接返回，否则构造、缓存并返回。
     *
     * @param expression 表达式原文
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 已解析的表达式对象，不为 {@code null}
     * @throws cn.hg.aifei.job.JobException 表达式为 {@code null}、空白或无法解析
     */
    private CronExpression compile(String expression, String taskName) {
        if (expression == null || expression.trim().isEmpty()) {
            throw JobErrors.illegalCron(taskName, expression, null);
        }
        CronExpression cached = cache.get(expression);
        if (cached != null) {
            return cached;
        }
        CronExpression parsed;
        try {
            parsed = new CronExpression(expression);
        } catch (ParseException e) {
            throw JobErrors.illegalCron(taskName, expression, e);
        }
        cache.put(expression, parsed);
        return parsed;
    }
}
