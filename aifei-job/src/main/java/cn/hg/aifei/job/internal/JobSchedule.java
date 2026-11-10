/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

/**
 * 节拍计算的唯一定义点：三种触发方式的差异全部收敛到 {@link #nextDelay(JobTask, long, long, long, boolean)}
 * 一个分叉点，fixedRate 的超时跳过判定收敛到 {@link #overduePlanTime(JobTask, long, long)}。
 *
 * <p><b>两个方法都是纯函数，不读时钟</b>：时刻由调用方（{@code JobRunner} / {@code JobWakeTask} /
 * {@code JobScheduler#start}）在明确的采样点取一次后传入。这样「同一语义只取一次时刻」与
 * 「跳过事件的倒挂防护」都成为调用方可验证的约定，而不是埋在本类里的隐含行为。</p>
 *
 * <p>{@code initialDelay} <b>只在本类的首次分支读取一次</b>，非首次分支完全不读它：首次之后节拍只由
 * 「上次开始 + 间隔」「上次结束 + 延迟」「cron 下次时刻」三者之一决定，被拒重排也复用同一分叉点
 * （{@code isFirst} 写死 {@code false}），因此重排不会再次消费首次延迟。</p>
 */
public final class JobSchedule {

    /**
     * 无下次触发、无跳过事件的返回值。
     */
    public static final long NONE = -1L;

    /**
     * cron 计算契约，只在 cron 分支使用。
     */
    private final CronCalculator cron;

    /**
     * @param cron cron 计算契约
     */
    public JobSchedule(CronCalculator cron) {
        if (cron == null) {
            throw new NullPointerException("cron calculator must not be null");
        }
        this.cron = cron;
    }

    /**
     * 计算「从现在起还需等待多久」才到下一个触发点。
     *
     * <p>首次（{@code isFirst}）：cron 取 {@code scheduleNow} 之后的第一个表达式时刻；固定频率 / 固定延迟
     * 直接返回 {@code initialDelay}。非首次：固定频率以「上次开始 + 间隔」为下次点、固定延迟以
     * 「上次结束 + 延迟」为下次点、cron 以 {@code scheduleNow} 之后的下一个时刻为下次点。下次点已过去
     * （任务耗时超过间隔、系统时间跳变）时返回 {@code 0} 表示立即重排，已过的触发点不补跑。</p>
     *
     * @param task 任务定义
     * @param lastStart 上次任务体实际开始执行时刻，epoch 毫秒；首次执行传 {@code 0}，被拒重排传当次的 {@code scheduleNow}
     * @param lastEnd 上次任务体结束时刻，epoch 毫秒；首次执行传 {@code 0}，被拒重排传当次的 {@code scheduleNow}
     * @param scheduleNow 本次节拍计算基准时刻，epoch 毫秒，由调用方采样一次
     * @param isFirst 是否首次排期（{@code true} 时只返回 {@code initialDelay} 或 cron 首个时刻）
     * @return 距离下次触发的毫秒数，恒 {@code >= 0}；无下次触发时返回 {@link #NONE}
     */
    public long nextDelay(JobTask task, long lastStart, long lastEnd, long scheduleNow, boolean isFirst) {
        JobTrigger trigger = task.trigger();
        if (isFirst) {
            return firstDelay(task, scheduleNow);
        }
        long nextPoint;
        if (trigger.kind() == JobTrigger.Kind.FIXED_RATE) {
            nextPoint = lastStart + trigger.period();
        } else if (trigger.kind() == JobTrigger.Kind.FIXED_DELAY) {
            nextPoint = lastEnd + trigger.delay();
        } else {
            nextPoint = cron.nextAfter(trigger.cron(), scheduleNow, task.name());
            if (nextPoint < 0L) {
                return NONE;
            }
        }
        return Math.max(0L, nextPoint - scheduleNow);
    }

    /**
     * 判定 fixedRate 的这一次触发是否已超时（结束时下一个计划点已过去），并给出跳过事件的<b>事件时间</b>。
     *
     * <p>返回的是<b>原计划触发时刻</b>（{@code lastStart + period}）而不是当前时刻：跳过事件描述的是
     * 「本该在那一刻执行却因为上一轮还没结束而没执行」，用当前时刻会让同一任务的事件时间晚于新基准，
     * 破坏「事件时间一律不晚于新基准 {@code now}」的倒挂防护。</p>
     *
     * <p>固定延迟与 cron 不产生此类跳过：固定延迟的下次点由结束时刻推出，天然不会「已过去」；cron 的
     * 已过时刻由 {@link #nextDelay} 直接归零处理（延迟执行，不作跳过记录）。</p>
     *
     * @param task 任务定义
     * @param lastStart 上次任务体实际开始执行时刻，epoch 毫秒
     * @param scheduleNow 本次判定基准时刻，epoch 毫秒，由调用方采样一次
     * @return 跳过事件的触发时间（即原计划触发时刻）；不构成跳过时返回 {@link #NONE}
     */
    public long overduePlanTime(JobTask task, long lastStart, long scheduleNow) {
        JobTrigger trigger = task.trigger();
        if (trigger.kind() != JobTrigger.Kind.FIXED_RATE) {
            return NONE;
        }
        long planTime = lastStart + trigger.period();
        return planTime <= scheduleNow ? planTime : NONE;
    }

    /**
     * 首次排期：固定频率 / 固定延迟返回首次延迟，cron 返回表达式首个时刻。
     *
     * @param task 任务定义
     * @param scheduleNow 本次节拍计算基准时刻，epoch 毫秒
     * @return 距离首次触发的毫秒数，恒 {@code >= 0}；无下次触发时返回 {@link #NONE}
     */
    private long firstDelay(JobTask task, long scheduleNow) {
        JobTrigger trigger = task.trigger();
        if (trigger.kind() == JobTrigger.Kind.CRON) {
            long nextPoint = cron.nextAfter(trigger.cron(), scheduleNow, task.name());
            return nextPoint < 0L ? NONE : Math.max(0L, nextPoint - scheduleNow);
        }
        return Math.max(0L, trigger.initialDelay());
    }
}
