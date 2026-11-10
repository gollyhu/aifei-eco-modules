/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

/**
 * 任务状态快照（不可变）：任务名、触发时间、阶段与异常。
 *
 * <p>{@code final} 类、字段全 {@code final}、无 setter、无懒加载 / 缓存字段，构造期一次赋值。触发时间为
 * {@code long}（epoch 毫秒），不使用 {@link java.util.Date} / {@link java.util.Calendar}（可变类型，
 * {@code final} 字段挡不住）。除异常引用外不可变；异常仅作故障记录、不参与调度判据，故不做防御性拷贝。</p>
 *
 * <p>同一任务的事件按「{@link Phase#STARTED} → 终态」顺序串行到达监听器。快照实例由模块内部装配，
 * 业务方只读取、不构造。不实现 {@code equals} / {@code hashCode}：无值比较语义需求，
 * 且 {@link JobKit#state()} 每次返回新实例，值相等语义会误导调用方。</p>
 */
public final class JobEvent {

    /**
     * 事件阶段。
     */
    public enum Phase {

        /**
         * 任务即将开始执行：在任务体之前发出，此时尚未产生结果。
         */
        STARTED,

        /**
         * 任务正常返回。
         */
        SUCCESS,

        /**
         * 任务抛出异常（含 {@link Error}），异常由 {@link JobEvent#error()} 取得。
         */
        FAILED,

        /**
         * 计划触发的这一次没有执行：提交时执行队列已满被拒、执行耗时已越过下一个计划点，或应用停机中断。
         */
        SKIPPED
    }

    /**
     * 任务名，格式为「类全名#方法名」。
     */
    private final String taskName;

    /**
     * 触发时间，epoch 毫秒。
     */
    private final long triggerTime;

    /**
     * 阶段。
     */
    private final Phase phase;

    /**
     * 故障原因；非 {@link Phase#FAILED} 阶段为 {@code null}。
     */
    private final Throwable error;

    /**
     * 禁止外部构造，只能经 {@link #of(String, long, Phase, Throwable)} 创建。
     *
     * @param taskName 任务名
     * @param triggerTime 触发时间，epoch 毫秒
     * @param phase 阶段
     * @param error 故障原因，可为 {@code null}
     */
    private JobEvent(String taskName, long triggerTime, Phase phase, Throwable error) {
        this.taskName = taskName;
        this.triggerTime = triggerTime;
        this.phase = phase;
        this.error = error;
    }

    /**
     * 装配一个事件快照；本方法是模块内部唯一的装配入口。
     *
     * @param taskName 任务名，格式为「类全名#方法名」
     * @param triggerTime 触发时间，epoch 毫秒
     * @param phase 阶段
     * @param error 故障原因；非 {@link Phase#FAILED} 阶段传 {@code null}
     * @return 新的不可变事件快照
     */
    public static JobEvent of(String taskName, long triggerTime, Phase phase, Throwable error) {
        return new JobEvent(taskName, triggerTime, phase, error);
    }

    /**
     * @return 任务名，格式为「类全名#方法名」
     */
    public String taskName() {
        return taskName;
    }

    /**
     * @return 触发时间，epoch 毫秒；{@link Phase#SKIPPED} 时取该次的原计划触发时刻
     */
    public long triggerTime() {
        return triggerTime;
    }

    /**
     * @return 阶段
     */
    public Phase phase() {
        return phase;
    }

    /**
     * @return 故障原因；非 {@link Phase#FAILED} 阶段返回 {@code null}
     */
    public Throwable error() {
        return error;
    }

    @Override
    public String toString() {
        return "JobEvent{taskName=" + taskName
                + ", phase=" + phase
                + ", triggerTime=" + triggerTime
                + ", error=" + error
                + "}";
    }
}
