/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.support;

import cn.hg.aifei.job.internal.CronCalculator;
import cn.hg.aifei.job.internal.JobErrors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link CronCalculator} 的测试替身：表达式是否「合法」以及「下次触发时刻」完全由预置映射决定，不解析表达式。
 *
 * <p>用途是把「表达式语义」与「依赖它的上层规则」解耦：</p>
 * <ul>
 *   <li>{@code CronExpressionCasesTest} 用<b>真实实现</b> {@code local.QuartzCronSupport} 验证表达式语义；</li>
 *   <li>{@code JobScheduleTest} / {@code JobScannerTest} 用本替身预置「下次时刻」与「非法表达式」，
 *       从而只验证「委托校验」「分叉计算」这些上层规则，断言值都是用例写死的字面量。</li>
 * </ul>
 *
 * <p>映射值为 {@link CronCalculator#NO_NEXT} 表示「表达式合法但永不再触发」；未出现在映射中的表达式一律视为非法，
 * 抛出与真实实现同源的 {@link JobErrors#illegalCron}，因此「消息含表达式原文」的断言仍然锁的是模块自身口径。</p>
 */
public final class StubCronCalculator implements CronCalculator {

    /**
     * 表达式原文到下次触发时刻的预置映射。
     */
    private final Map<String, Long> nextTimes;

    /**
     * 已通过 {@link #validate(String, String)} 的表达式原文，按调用顺序记录。
     */
    private final List<String> validated = new ArrayList<>();

    /**
     * @param nextTimes 表达式原文到下次触发时刻的预置映射
     */
    public StubCronCalculator(Map<String, Long> nextTimes) {
        this.nextTimes = new LinkedHashMap<>(nextTimes);
    }

    /**
     * 便捷构造：单个合法表达式与其下次触发时刻。
     *
     * @param expression 表达式原文
     * @param nextTime 下次触发时刻，epoch 毫秒
     * @return 替身
     */
    public static StubCronCalculator accepting(String expression, long nextTime) {
        Map<String, Long> nextTimes = new LinkedHashMap<>();
        nextTimes.put(expression, nextTime);
        return new StubCronCalculator(nextTimes);
    }

    @Override
    public void validate(String expression, String taskName) {
        if (expression == null || !nextTimes.containsKey(expression)) {
            throw JobErrors.illegalCron(taskName, expression, null);
        }
        validated.add(expression);
    }

    @Override
    public long nextAfter(String expression, long epochMillis, String taskName) {
        Long next = expression == null ? null : nextTimes.get(expression);
        if (next == null) {
            throw JobErrors.illegalCron(taskName, expression, null);
        }
        return next.longValue();
    }

    /**
     * @return 已通过校验的表达式原文，按调用顺序
     */
    public List<String> validatedExpressions() {
        return Collections.unmodifiableList(new ArrayList<>(validated));
    }
}
