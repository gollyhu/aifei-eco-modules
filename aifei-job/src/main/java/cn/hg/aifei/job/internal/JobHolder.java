/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.hg.aifei.job.JobEvent;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * {@code JobKit} 的唯一写入通道：启动时安装注册表与状态记录，停机时清除。
 *
 * <p>未安装（尚未启动）或已清除（已停机）时取用返回<b>空集合且不抛异常</b>——观测面在停机窗口不应开始抛异常，
 * 这也是本模块对「门面未装配即失败」的唯一有意让步，只覆盖这两个只读方法。</p>
 */
public final class JobHolder {

    /**
     * 安装与清除共用的锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 当前装配的任务注册表；未安装时为 {@code null}。
     */
    private static JobRegistry registry;

    /**
     * 当前装配的状态记录；未安装时为 {@code null}。
     */
    private static JobState state;

    /**
     * 禁止创建工具类实例。
     */
    private JobHolder() {
    }

    /**
     * 安装注册表与状态记录，覆盖既有安装。
     *
     * @param jobRegistry 任务注册表
     * @param jobState 状态记录
     */
    public static void install(JobRegistry jobRegistry, JobState jobState) {
        synchronized (LOCK) {
            registry = jobRegistry;
            state = jobState;
        }
    }

    /**
     * 清除安装；清除后取用立即返回空集合。
     */
    public static void clear() {
        synchronized (LOCK) {
            registry = null;
            state = null;
        }
    }

    /**
     * @return 不可变的、按任务名排序的任务名集合；未安装时返回空集合
     */
    public static Set<String> names() {
        synchronized (LOCK) {
            if (registry == null) {
                return Collections.emptySet();
            }
            return registry.names();
        }
    }

    /**
     * @return 不可变的、按任务名排序的终态快照；未安装时返回空映射
     */
    public static Map<String, JobEvent> state() {
        synchronized (LOCK) {
            if (state == null) {
                return Collections.emptyMap();
            }
            return state.snapshot();
        }
    }
}
