/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.job.JobEvent;
import java.util.concurrent.RejectedExecutionException;

/**
 * 待触发实例：唤醒层队列中的一个 Runnable，代表「某任务在 {@code plannedTime} 这一刻该触发一次」。
 *
 * <p>同一任务恒至多一个待触发实例：下一个实例只在「上一个执行结束」（{@link JobRunner} 的 {@code finally}）或
 * 「上一个提交被拒」（本类的 {@code catch}）时投递。因此不重叠、不排队、不堆叠是<b>结构属性</b>，不是附加守卫。</p>
 *
 * <p>本类只做一件事：提交。到点后<b>不比较时间</b>——唤醒线程因 GC / 系统调度延迟取到已到期任务属延迟执行，
 * 不是补跑，照常提交即可，节拍会由执行层采样的 {@code lastStart} 随之后移。</p>
 *
 * <p><b>拒绝记账的唯一入口</b>就是本类 {@link #run()} 的 {@code catch (RejectedExecutionException)}：
 * 执行层用 {@code AbortPolicy}，队列满或已停机时提交必然抛出；被拒任务进不了执行层，{@link JobRunner} 的
 * {@code finally} 永远不会执行，若不在同一处就地重排，该任务将永久停摆。模块<b>不另设</b>自定义
 * {@code RejectedExecutionHandler} 再记一次账。</p>
 */
final class JobWakeTask implements Runnable {

    /**
     * 任务定义。
     */
    private final JobTask task;

    /**
     * 计划触发时刻，epoch 毫秒；同时是本实例被拒时 {@code SKIPPED} 事件的<b>事件时间</b>。
     */
    private final long plannedTime;

    /**
     * 调度内核：提交执行层、记账 / 分发、求下次延迟、续排投递都经它，保证同类规则只有一个定义点。
     */
    private final JobScheduler scheduler;

    /**
     * 本类日志；实例类在字段初始化期取用，停机续排终止的 {@code warn} 在唤醒线程上产生。
     */
    private final Log log = Log.get(JobWakeTask.class);

    /**
     * @param task 任务定义
     * @param plannedTime 计划触发时刻，epoch 毫秒
     * @param scheduler 调度内核
     */
    JobWakeTask(JobTask task, long plannedTime, JobScheduler scheduler) {
        this.task = task;
        this.plannedTime = plannedTime;
        this.scheduler = scheduler;
    }

    /**
     * 到点：把执行单元提交给执行层；被拒时就地记账并重排。
     *
     * <p>被拒路径的固定口径（决策 7）：① 记 {@code SKIPPED}，事件时间取<b>本次原计划触发时刻</b>
     * {@code plannedTime}（不能取重置基准的 {@code now}——事件描述的是「本该在那一刻触发却没触发」）；
     * ② 采样一次 {@code now}，把节拍基准重置为它：{@code lastStart} 与 {@code lastEnd} 同置该 {@code now}、
     * {@code scheduleNow} 也是它（一次读时钟，不得两次）；③ {@code isFirst} 写死 {@code false}，不重复消费
     * {@code initialDelay}；④ 经 {@link JobScheduler#reschedule(JobTask, long)} 就地重排。</p>
     *
     * <p>停机竞态下就地重排本身也可能被拒（唤醒层已 {@code shutdown()}）：同一处静默终止并记 {@code warn}，
     * 不新增记账点、不外抛——唤醒线程不能因停机竞态被打断。</p>
     */
    @Override
    public void run() {
        try {
            scheduler.execute(new JobRunner(task, plannedTime, scheduler));
        } catch (RejectedExecutionException e) {
            recordSkipped();
            long now = System.currentTimeMillis();
            long delay = scheduler.schedule().nextDelay(task, now, now, now, false);
            try {
                scheduler.reschedule(task, delay);
            } catch (RejectedExecutionException rejected) {
                log.warn("job " + task.name() + " rescheduling terminated by shutdown");
            }
        }
    }

    /**
     * 记一条「提交被拒」的 {@code SKIPPED}：事件时间取原计划触发时刻，先记账再回调（回调内已能观测到本次终态）。
     */
    private void recordSkipped() {
        JobEvent event = JobEvents.assemble(JobEvent.Phase.SKIPPED, task.name(), plannedTime, null);
        scheduler.state().record(event);
        scheduler.events().dispatch(event);
    }
}
