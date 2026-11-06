/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.internal;

import cn.hg.aifei.lock.LockException;

/**
 * 异常消息构造与包装规则的唯一定义点。
 *
 * <p>非承诺 API。运行期包装规则：{@link IllegalArgumentException} 原样抛出、
 * {@link LockException} 不重复包装、其余 {@link RuntimeException} 包装并保留 cause；{@code Error}
 * 由调用方不捕获。启动期失败一律用 {@link IllegalStateException} 表达，不走运行期包装规则。</p>
 */
public final class LockErrors {

    /**
     * 禁止创建工具类实例。
     */
    private LockErrors() {
    }

    /**
     * 按运行期包装规则返回待抛出的异常。
     *
     * @param operation 操作名（{@code acquire} / {@code release} / {@code renew}）
     * @param name 锁名
     * @param cause 存储侧抛出的原始异常
     * @return 参数非法与已有 {@code LockException} 时返回 {@code cause} 本身，否则返回包装后的异常
     */
    public static RuntimeException wrap(String operation, String name, RuntimeException cause) {
        if (cause instanceof IllegalArgumentException || cause instanceof LockException) {
            return cause;
        }
        return new LockException("lock " + operation + " failed, name: " + name, cause);
    }

    /**
     * 后端不支持条件操作时的启动期异常。
     *
     * @return 消息固定的异常
     */
    public static IllegalStateException notSharedStore() {
        return new IllegalStateException("lock backend is not a shared store");
    }

    /**
     * 能力查询自身抛异常时的启动期异常。
     *
     * @param cause 能力查询抛出的原始异常
     * @return 保留 cause 的异常
     */
    public static IllegalStateException capabilityCheckFailed(RuntimeException cause) {
        return new IllegalStateException("lock backend capability check failed", cause);
    }

    /**
     * 主缓存实例取不到时的启动期异常。
     *
     * @param cause cache 侧抛出的原始异常
     * @return 保留 cause 的异常
     */
    public static IllegalStateException mainInstanceNotInitialized(RuntimeException cause) {
        return new IllegalStateException("cache __MAIN__ instance is not initialized", cause);
    }

    /**
     * 未装配时的取用期异常。
     *
     * @return 消息固定的异常
     */
    public static IllegalStateException notInitialized() {
        return new IllegalStateException("LockHolder has not been initialized. Add LockPlugin first.");
    }
}
