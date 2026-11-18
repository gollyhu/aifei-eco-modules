/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import java.util.Set;

/**
 * 框架能力端口（非承诺 API）：容器按类型取 + 包扫描；唯一实现在 {@code aifei} 接入包。
 *
 * <p>启动期探测与扫描的真实实现由 {@code AuditPlugin} 注入，{@code internal} 因此保持零框架引用。</p>
 */
public interface AuditEnvironment {

    /**
     * 按类型取容器内实例。
     *
     * @param type 类型
     * @param <T>  类型
     * @return 容器内实例
     * @throws RuntimeException 容器内无该类型（由 {@code AuditSpi} 捕获后判定「未装配」）
     */
    <T> T lookup(Class<T> type);

    /**
     * 扫描包内的类。
     *
     * @param packageName 包名
     * @return 包内的类集合；空包返回空集合
     */
    Set<Class<?>> scan(String packageName);
}
