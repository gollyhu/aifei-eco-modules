/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import cn.hg.aifei.job.internal.JobHolder;
import java.util.Map;
import java.util.Set;

/**
 * 只读观测门面：查看已注册的任务名与各任务最近一次终态。
 *
 * <p>只有这两个只读方法，不提供注册、触发与取消入口；应用启动时装配、停机时清除，业务方只读取。
 * <b>未启动或已停机时两个方法返回空集合，不抛异常</b>，便于管理端点与演示页在停机窗口继续轮询。</p>
 *
 * <p>观测口径：{@link #state()} 是「最后已知结果」，除 {@link JobEvent.Phase#STARTED} 外的阶段才会进入，
 * 且执行中的任务保留上一次终态、不会暂时消失；因此 {@code state()} 无法区分「正在执行」与「空闲等待」，
 * 执行中视图只能由 {@link JobListener} 的事件流提供。</p>
 */
public final class JobKit {

    /**
     * 禁止创建工具类实例。
     */
    private JobKit() {
    }

    /**
     * 已注册的任务名。
     *
     * @return 不可变的、按任务名排序的任务名集合；未启动或已停机时返回空集合
     */
    public static Set<String> names() {
        return JobHolder.names();
    }

    /**
     * 各任务最近一次终态快照。
     *
     * @return 不可变的、按任务名排序的映射，key 为任务名；未启动、已停机或尚无任务产生终态时返回空映射；
     *         每次调用都返回新的 {@code Map} 与新的 {@link JobEvent} 值，不暴露内部记录实例、不返回活视图
     */
    public static Map<String, JobEvent> state() {
        return JobHolder.state();
    }
}
