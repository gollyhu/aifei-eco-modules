/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.job.JobEvent;
import cn.hg.aifei.job.JobListener;

/**
 * 事件装配与监听器分发的唯一定义点。
 *
 * <p>装配只有 {@link #assemble(JobEvent.Phase, String, long, Throwable)} 一处，内部只经
 * {@link JobEvent#of(String, long, JobEvent.Phase, Throwable)}；调度内核不直接构造 {@link JobEvent}，
 * 保证事件形态（字段顺序、阶段取值、异常归属）只有一套。</p>
 *
 * <p>分发只有 {@link #dispatch(JobEvent)} 一处：同步回调、运行在产生该事件的线程上，回调抛出的任何
 * {@link Throwable} 都被就地捕获记录、<b>绝不上抛</b>——监听器属可选观测设施，不得影响任务执行、
 * 终态记账与后续触发。无监听器时直接返回，事件照常产生与记账，只是没有送达方。</p>
 */
public final class JobEvents {

    /**
     * 监听器；容器中没有实现时为 {@code null}（视为无观测，不视为错误）。
     */
    private final JobListener listener;

    /**
     * 本类日志：实例类在字段初始化期取用，未装配日志工厂时的失败点落在构造期而不是分发期的 {@code catch} 内。
     */
    private final Log log = Log.get(JobEvents.class);

    /**
     * @param listener 监听器；{@code null} 表示容器中没有该类型的实现
     */
    public JobEvents(JobListener listener) {
        this.listener = listener;
    }

    /**
     * 装配一个事件快照。
     *
     * @param phase 阶段
     * @param taskName 任务名，格式为「类全名#方法名」
     * @param triggerTime 触发时间，epoch 毫秒；{@link JobEvent.Phase#SKIPPED} 时取该次的原计划触发时刻
     * @param error 故障原因；非 {@link JobEvent.Phase#FAILED} 阶段传 {@code null}
     * @return 新的不可变事件快照
     */
    public static JobEvent assemble(JobEvent.Phase phase, String taskName, long triggerTime, Throwable error) {
        return JobEvent.of(taskName, triggerTime, phase, error);
    }

    /**
     * 同步分发事件给监听器；无监听器直接返回，回调异常被隔离。
     *
     * @param event 事件快照；为 {@code null} 时忽略
     */
    public void dispatch(JobEvent event) {
        if (listener == null || event == null) {
            return;
        }
        try {
            listener.onEvent(event);
        } catch (Throwable t) {
            log.error("job listener failed on " + event.taskName() + " " + event.phase()
                    + " event at " + event.triggerTime(), t);
        }
    }
}
