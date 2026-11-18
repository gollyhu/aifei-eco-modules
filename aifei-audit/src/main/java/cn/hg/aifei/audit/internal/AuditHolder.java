/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

/**
 * 运行期状态唯一持有者（非承诺 API）：唯一共享状态是一个 volatile 快照。
 *
 * <p>「未启动 / 已停止 / 已启用但未装配」三态由快照为 null 与非空但 sink 为 null 表达，热路径无锁。</p>
 */
public final class AuditHolder {

    private static volatile AuditRuntime current;

    private AuditHolder() {
    }

    /**
     * @param runtime 生效快照
     */
    public static void install(AuditRuntime runtime) {
        current = runtime;
    }

    /**
     * 清除快照（停止采集，已注册拦截器随后读到 null 即零采集）。
     */
    public static void clear() {
        current = null;
    }

    /**
     * @return 当前快照；未启动 / 已停止为 {@code null}
     */
    public static AuditRuntime snapshot() {
        return current;
    }
}
