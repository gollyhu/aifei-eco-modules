/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.internal;

/**
 * 运行期 {@link LockStore} 的持有者：装配入口写入，门面读取，测试经它注入替身。
 *
 * <p>非承诺 API。只做注册与读取，不做工厂。</p>
 */
public final class LockHolder {

    /**
     * 当前生效的存储实现；未装配时为 {@code null}。
     */
    private static volatile LockStore store;

    /**
     * 禁止创建工具类实例。
     */
    private LockHolder() {
    }

    /**
     * 注册运行期存储实现。
     *
     * @param lockStore 存储实现
     * @throws IllegalArgumentException {@code lockStore} 为 {@code null}
     */
    public static void register(LockStore lockStore) {
        if (lockStore == null) {
            throw new IllegalArgumentException("lock store can not be null");
        }
        store = lockStore;
    }

    /**
     * 清除运行期存储实现；清除后 {@link #get()} 立即失效。
     */
    public static void clear() {
        store = null;
    }

    /**
     * 取当前存储实现。
     *
     * @return 存储实现
     * @throws IllegalStateException 尚未注册
     */
    public static LockStore get() {
        LockStore current = store;
        if (current == null) {
            throw LockErrors.notInitialized();
        }
        return current;
    }
}
