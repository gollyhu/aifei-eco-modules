/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.cache.internal.Config;
import java.time.Duration;
import java.util.Set;

/**
 * {@link Counter} 的静态工具类：不注入也能取到计数器实例，或用静态方法直接操作主计数器。
 *
 * <p>「主计数器」指由主缓存派生出的计数器，与主缓存同源（共用命名空间与默认 TTL）。配置了
 * 扩展实例时，用 {@link #use(String)} 按实例名取；取到的是同一个对象，可以放心存下来反复
 * 使用。</p>
 *
 * <p>下列镜像方法的参数与异常契约同 {@link Counter} 的对应方法，门面只做转发：不重复校验、不转换异常。
 * 它们都要先取实例，因此与 {@link #use()} 一样，可能在插件尚未启动或已经停止时抛
 * {@link IllegalStateException}。</p>
 */
public final class CounterUtil {

    private CounterUtil() {
    }

    /**
     * 取主计数器。
     *
     * @return 主计数器实例
     * @throws IllegalStateException 插件尚未启动或已经停止
     */
    public static Counter use() {
        return use(Config.MAIN_INSTANCE_NAME);
    }

    /**
     * 按实例名取计数器实例；实例名在配置项 {@code cache.names} 里声明。
     *
     * @param instanceName 实例名；主计数器用 {@code use()}，无需传名字
     * @return 该实例的计数器
     * @throws IllegalStateException 插件尚未启动或已经停止
     * @throws IllegalArgumentException 实例名为空、含非法字符或未注册
     */
    public static Counter use(String instanceName) {
        return CacheManager.getCounter(instanceName);
    }

    // ─────────── 以下静态方法与 Counter 的同名方法一一对应，一律作用于主计数器 ───────────

    /**
     * 读取主计数器的计数值；规则同 {@link Counter#get(String, String)}。
     */
    public static Long get(String counterName, String key) {
        return use().get(counterName, key);
    }

    /**
     * 增加计数值，固定窗口语义；规则同 {@link Counter#increase(String, String, long, Duration)}。
     */
    public static long increase(String counterName, String key, long step, Duration ttl) {
        return use().increase(counterName, key, step, ttl);
    }

    /**
     * 使用秒为单位增加计数值；规则同 {@link Counter#increase(String, String, long, int)}。
     */
    public static long increase(String counterName, String key, long step, int ttlSeconds) {
        return use().increase(counterName, key, step, ttlSeconds);
    }

    /**
     * 增加计数值并刷新 TTL；规则同 {@link Counter#increaseAndRefreshTtl(String, String, long, Duration)}。
     */
    public static long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return use().increaseAndRefreshTtl(counterName, key, step, ttl);
    }

    /**
     * 使用秒为单位增加计数值并刷新 TTL；规则同 {@link Counter#increaseAndRefreshTtl(String, String, long, int)}。
     */
    public static long increaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds) {
        return use().increaseAndRefreshTtl(counterName, key, step, ttlSeconds);
    }

    /**
     * 减少计数值，固定窗口语义；规则同 {@link Counter#decrease(String, String, long, Duration)}。
     */
    public static long decrease(String counterName, String key, long step, Duration ttl) {
        return use().decrease(counterName, key, step, ttl);
    }

    /**
     * 使用秒为单位减少计数值；规则同 {@link Counter#decrease(String, String, long, int)}。
     */
    public static long decrease(String counterName, String key, long step, int ttlSeconds) {
        return use().decrease(counterName, key, step, ttlSeconds);
    }

    /**
     * 减少计数值并刷新 TTL；规则同 {@link Counter#decreaseAndRefreshTtl(String, String, long, Duration)}。
     */
    public static long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl) {
        return use().decreaseAndRefreshTtl(counterName, key, step, ttl);
    }

    /**
     * 使用秒为单位减少计数值并刷新 TTL；规则同 {@link Counter#decreaseAndRefreshTtl(String, String, long, int)}。
     */
    public static long decreaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds) {
        return use().decreaseAndRefreshTtl(counterName, key, step, ttlSeconds);
    }

    /**
     * 删除单个计数项；规则同 {@link Counter#remove(String, String)}。
     */
    public static void remove(String counterName, String key) {
        use().remove(counterName, key);
    }

    /**
     * 清空该计数名称下的全部计数项；规则同 {@link Counter#clear(String)}。
     */
    public static void clear(String counterName) {
        use().clear(counterName);
    }

    /**
     * 返回主计数器曾被创建过的计数名称集合；规则同 {@link Counter#counterNames()}。
     */
    public static Set<String> counterNames() {
        return use().counterNames();
    }

    /**
     * 返回主计数器的默认 TTL；规则同 {@link Counter#ttl()}。
     */
    public static Duration ttl() {
        return use().ttl();
    }

    // ─────────── 省略 TTL 重载：缺省取主计数器 ttl() ───────────

    /**
     * 使用主计数器的默认 TTL 增加计数值，固定窗口语义；规则同 {@link Counter#increase(String, String, long)}。
     */
    public static long increase(String counterName, String key, long step) {
        return use().increase(counterName, key, step);
    }

    /**
     * 使用主计数器的默认 TTL 增加计数值并刷新 TTL；规则同 {@link Counter#increaseAndRefreshTtl(String, String, long)}。
     */
    public static long increaseAndRefreshTtl(String counterName, String key, long step) {
        return use().increaseAndRefreshTtl(counterName, key, step);
    }

    /**
     * 使用主计数器的默认 TTL 减少计数值，固定窗口语义；规则同 {@link Counter#decrease(String, String, long)}。
     */
    public static long decrease(String counterName, String key, long step) {
        return use().decrease(counterName, key, step);
    }

    /**
     * 使用主计数器的默认 TTL 减少计数值并刷新 TTL；规则同 {@link Counter#decreaseAndRefreshTtl(String, String, long)}。
     */
    public static long decreaseAndRefreshTtl(String counterName, String key, long step) {
        return use().decreaseAndRefreshTtl(counterName, key, step);
    }

}

