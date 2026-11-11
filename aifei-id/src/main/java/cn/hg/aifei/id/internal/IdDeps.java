/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

/**
 * 可选依赖探测的唯一定义点：本类是全模块唯一出现 {@code Class.forName} 的地方。
 *
 * <p>{@code aifei-cache} 与 {@code aifei-lock} 都是 {@code optional} 依赖，缺失时的默认后果是
 * {@code NoClassDefFoundError}（发生时机不可控，可能在首次发号的热路径上），对使用方不可读。本模块的
 * 策略与 workerId 来源由<b>配置在运行期决定</b>，因此在装配分支里按需探测，缺失即给出「请加哪个坐标」
 * 的明确消息，绝不降级。</p>
 *
 * <p>探测只做 {@code Class.forName(name, false, loader)}（不初始化类、不实例化、不扫描 SPI），探测通过后
 * 才由调用方引用相应类型。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class IdDeps {

    /**
     * 计数门面类名，代表 {@code aifei-cache} 模块可用。
     */
    private static final String COUNTER_KIT_CLASS_NAME = "cn.hg.aifei.cache.CounterKit";

    /**
     * 锁门面类名，代表 {@code aifei-lock} 模块可用。
     */
    private static final String LOCK_KIT_CLASS_NAME = "cn.hg.aifei.lock.LockKit";

    /**
     * 禁止创建工具类实例。
     */
    private IdDeps() {
    }

    /**
     * 探测 {@code aifei-cache} 是否在 classpath 上。
     *
     * @throws cn.hg.aifei.id.IdException {@code aifei-cache} 缺失
     */
    public static void requireCache() {
        if (!isPresent(COUNTER_KIT_CLASS_NAME)) {
            throw IdErrors.cacheMissing();
        }
    }

    /**
     * 探测 {@code aifei-lock} 是否在 classpath 上。
     *
     * @throws cn.hg.aifei.id.IdException {@code aifei-lock} 缺失
     */
    public static void requireLock() {
        if (!isPresent(LOCK_KIT_CLASS_NAME)) {
            throw IdErrors.lockMissing();
        }
    }

    /**
     * 判断指定类是否可被加载（不初始化、不实例化）。
     *
     * @param className 全限定类名
     * @return 可加载时 {@code true}；类不存在或其依赖缺失（{@link LinkageError}）时 {@code false}
     */
    private static boolean isPresent(String className) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = IdDeps.class.getClassLoader();
        }
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            return false;
        }
    }
}
