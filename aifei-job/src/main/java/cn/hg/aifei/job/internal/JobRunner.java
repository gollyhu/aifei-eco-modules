/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.job.JobEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.RejectedExecutionException;

/**
 * 执行单元：一次触发在执行层线程上跑完「事件 → 任务体 → 记账 → 跳过判定 → 续排」全过程。
 *
 * <p><b>顺序不可交换</b>：进入执行 → 采样 {@code STARTED} 的事件时间 → 装配并同步分发
 * {@code STARTED} → <b>回调返回后</b>才采样 {@code lastStart} → 调任务体 → 记终态 → fixedRate 超时跳过 →
 * {@code finally} 采样 {@code lastEnd} 并续排。{@code STARTED} 在 {@code lastStart} 之前发出，是为了让「慢监听器」
 * 不计入 {@code lastEnd - lastStart}、不参与 fixedRate 的耗时判定，从而不改变调度行为（监听器属观测设施）。</p>
 *
 * <p><b>异常隔离</b>：任务体抛出的任何 {@link Throwable}（含 {@link Error}，否则会杀死池线程使该任务链
 * 断裂）都被就地捕获，解包 {@link InvocationTargetException#getTargetException()} 作为事件原因，记 {@code FAILED}
 * 并留堆栈；不做重试，续排照常。异常绝不被重新抛出。</p>
 *
 * <p><b>终态先记账再回调</b>：{@code record(终态)} 之后才 {@code dispatch(终态)}，故回调内调用 {@code JobKit.state()}
 * 已能看到本次终态；{@code STARTED} 本就不记账（回调内看到的是上一次终态）。</p>
 *
 * <p><b>续排只有一处（正常路径）</b>：{@code finally} 里求下次延迟并投递；若停机竞态下唤醒层已关闭，投递抛
 * {@link RejectedExecutionException}——此处静默终止并记 {@code warn}，<b>不得外抛</b>（外抛会覆盖任务自身异常，
 * 也会让池线程带着未处理异常结束）。</p>
 */
final class JobRunner implements Runnable {

    /**
     * 任务定义。
     */
    private final JobTask task;

    /**
     * 计划触发时刻，epoch 毫秒；停机 {@code shutdownNow()} 时作为该次 {@code SKIPPED} 的事件时间。
     */
    private final long plannedTime;

    /**
     * 调度内核：记账 / 分发、求下次延迟、续排投递都经它，保证同类规则只有一个定义点。
     */
    private final JobScheduler scheduler;

    /**
     * 本类日志；实例类在字段初始化期取用，故障堆栈在池线程上产生。
     */
    private final Log log = Log.get(JobRunner.class);

    /**
     * @param task 任务定义
     * @param plannedTime 计划触发时刻，epoch 毫秒
     * @param scheduler 调度内核
     */
    JobRunner(JobTask task, long plannedTime, JobScheduler scheduler) {
        this.task = task;
        this.plannedTime = plannedTime;
        this.scheduler = scheduler;
    }

    /**
     * 执行一次触发；无论任务体成功、失败还是抛出 {@link Error}，都会走到续排。
     */
    @Override
    public void run() {
        long lastStart = 0L;
        try {
            long eventTime = System.currentTimeMillis();
            JobEvent started = JobEvents.assemble(JobEvent.Phase.STARTED, task.name(), eventTime, null);
            scheduler.events().dispatch(started);
            // 回调返回后才采样：lastStart 的语义是「任务体实际开始」，队列等待与监听器耗时都不计入。
            lastStart = System.currentTimeMillis();
            Throwable error = null;
            try {
                task.invoke(started);
            } catch (Throwable thrown) {
                error = unwrap(thrown);
            }
            long endTime = System.currentTimeMillis();
            if (error == null) {
                record(JobEvent.Phase.SUCCESS, endTime, null);
            } else {
                log.error("job " + task.name() + " failed", error);
                record(JobEvent.Phase.FAILED, endTime, error);
            }
            // endTime 用于终态与超时判定，续排基准 lastEnd 在 finish() 内重新采样，语义不同故两处各取一次时钟
            skipOverdue(lastStart, endTime);
        } finally {
            finish(lastStart);
        }
    }

    /**
     * @return 任务定义，供停机 {@code shutdownNow()} 时取任务名记账
     */
    JobTask task() {
        return task;
    }

    /**
     * @return 计划触发时刻，epoch 毫秒，供停机 {@code shutdownNow()} 时作 {@code SKIPPED} 的事件时间
     */
    long plannedTime() {
        return plannedTime;
    }

    /**
     * 记一条终态：<b>先记账再回调</b>，回调内已能观测到本次终态。
     *
     * @param phase 终态阶段
     * @param triggerTime 事件时间，epoch 毫秒
     * @param error 故障原因；非 {@link JobEvent.Phase#FAILED} 传 {@code null}
     */
    private void record(JobEvent.Phase phase, long triggerTime, Throwable error) {
        JobEvent event = JobEvents.assemble(phase, task.name(), triggerTime, error);
        scheduler.state().record(event);
        scheduler.events().dispatch(event);
    }

    /**
     * fixedRate 超时跳过：执行结束时下一个计划点已过去，则记一条 {@code SKIPPED}。
     *
     * <p>事件时间取 {@code overduePlanTime} 返回的<b>原计划触发时刻</b>（{@code lastStart + period}）而不是当前时刻，
     * 否则事件时间会晚于新基准、破坏倒挂防护。固定延迟与 cron 不产生此类跳过，判定完全由
     * {@link JobSchedule#overduePlanTime(JobTask, long, long)} 给出。</p>
     *
     * @param lastStart 本次任务体实际开始时刻，epoch 毫秒
     * @param scheduleNow 本次判定基准时刻，epoch 毫秒（复用终态的事件时间，只取一次时钟）
     */
    private void skipOverdue(long lastStart, long scheduleNow) {
        long planTime = scheduler.schedule().overduePlanTime(task, lastStart, scheduleNow);
        if (planTime < 0L) {
            return;
        }
        record(JobEvent.Phase.SKIPPED, planTime, null);
    }

    /**
     * 续排（正常路径的唯一一处）：采样 {@code lastEnd}，求下次延迟并投递；停机竞态下投递被拒则静默终止。
     *
     * @param lastStart 本次任务体实际开始时刻，epoch 毫秒；若任务体未真正开始（如提前出错）则为 {@code 0}，
     *         此时固定频率分支会退化为「立即重排」，不会丢失任务链
     */
    private void finish(long lastStart) {
        long lastEnd = System.currentTimeMillis();
        try {
            long delay = scheduler.schedule().nextDelay(task, lastStart, lastEnd, lastEnd, false);
            scheduler.reschedule(task, delay);
        } catch (RejectedExecutionException e) {
            log.warn("job " + task.name() + " rescheduling terminated by shutdown");
        }
    }

    /**
     * 解包反射调用包装，取出任务体真正抛出的原因。
     *
     * @param thrown 捕获到的异常或错误
     * @return 任务体自身抛出的原因；非反射包装时原样返回
     */
    private static Throwable unwrap(Throwable thrown) {
        if (thrown instanceof InvocationTargetException) {
            Throwable target = ((InvocationTargetException) thrown).getTargetException();
            if (target != null) {
                return target;
            }
        }
        return thrown;
    }
}
