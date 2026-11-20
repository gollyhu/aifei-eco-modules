/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.hg.aifei.job.JobEvent;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 状态记账的唯一定义点：只记录终态（{@code SUCCESS} / {@code FAILED} / {@code SKIPPED}），
 * {@code STARTED} 直接忽略。
 *
 * <p>「只收终态」是观测口径的实现依据：{@code STARTED} 既不覆盖也不清除上一次终态，因此执行中的任务
 * 不会从观测面消失；从未产生终态的任务不会出现。快照每次都是新的不可变 {@code Map} 与新的
 * {@link JobEvent} 值，不暴露内部记录实例、不返回活视图。记账在多个执行线程上并发发生，故全部方法同步。</p>
 */
public final class JobState {

    /**
     * 任务名到最近一次终态快照的映射，按任务名排序。
     */
    private final Map<String, JobEvent> terminal = new TreeMap<>();

    /**
     * 记录一次终态；{@code event} 为 {@code null}、{@code phase} 为 {@code null} 或 {@code Phase.STARTED} 时忽略。
     *
     * @param event 事件快照；为 {@code null} 或 {@code phase} 为 {@code null} 时忽略
     */
    public synchronized void record(JobEvent event) {
        if (event == null || event.phase() == null) {
            return;
        }
        if (event.phase() == JobEvent.Phase.STARTED) {
            return;
        }
        terminal.put(event.taskName(), event);
    }

    /**
     * 取最近一次终态快照。
     *
     * @return 新的不可变映射，key 为任务名、value 为复制的 {@link JobEvent} 新实例；无记录时返回空映射
     */
    public synchronized Map<String, JobEvent> snapshot() {
        Map<String, JobEvent> snapshot = new TreeMap<>();
        for (Map.Entry<String, JobEvent> entry : terminal.entrySet()) {
            JobEvent value = entry.getValue();
            snapshot.put(entry.getKey(), JobEvent.of(value.taskName(), value.triggerTime(),
                    value.phase(), value.error()));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
