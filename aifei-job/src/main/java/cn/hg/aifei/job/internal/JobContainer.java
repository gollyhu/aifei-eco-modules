/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

/**
 * 容器访问契约：把「按类型取实例」与「单例 / 多例判定」抽象成两个与框架无关的方法，
 * 使 {@code internal} 包不依赖任何框架类型。
 *
 * <p>框架语义的映射实现放在接入包，本包只使用契约。</p>
 */
public interface JobContainer {

    /**
     * 按类型取实例。
     *
     * @param type 类型
     * @param <T> 类型参数
     * @return 容器内的实例；取不到时返回 {@code null} 或抛异常，两种表达都由调用方统一视为「取不到」
     */
    <T> T get(Class<T> type);

    /**
     * 判定类型在容器中的单例 / 多例语义。
     *
     * @param type 类型
     * @return {@code true} 表示单例；{@code false} 表示多例
     */
    boolean isSingleton(Class<?> type);
}
