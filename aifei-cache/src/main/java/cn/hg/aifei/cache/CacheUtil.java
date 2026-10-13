/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.cache.internal.Config;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * {@link Cache} 的静态工具类：不注入也能取到缓存实例，或用静态方法直接操作主缓存。
 *
 * <p>「主缓存」指配置里 {@code cache.*} 那一套，应用启动后就存在。配置了扩展实例时，用
 * {@link #use(String)} 按实例名取；取到的是与 {@code @Inject} 注入的同一个对象，可以放心存下来
 * 反复使用。</p>
 *
 * <p>下列镜像方法的参数与异常契约同 {@link Cache} 的对应方法，门面只做转发：不重复校验、不转换异常。
 * 它们都要先取实例，因此与 {@link #use()} 一样，可能在插件尚未启动或已经停止时抛
 * {@link IllegalStateException}。</p>
 */
public final class CacheUtil {

    private CacheUtil() {
    }

    /**
     * 取主缓存。
     *
     * @return 主缓存实例
     * @throws IllegalStateException 插件尚未启动或已经停止
     */
    public static Cache use() {
        return use(Config.MAIN_INSTANCE_NAME);
    }

    /**
     * 按实例名取缓存实例；实例名在配置项 {@code cache.names} 里声明。
     *
     * @param instanceName 实例名；主缓存用 {@code use()}，无需传名字
     * @return 该实例的缓存
     * @throws IllegalStateException 插件尚未启动或已经停止
     * @throws IllegalArgumentException 实例名为空、含非法字符或未注册
     */
    public static Cache use(String instanceName) {
        return CacheManager.getCache(instanceName);
    }

    // ─────────── 以下静态方法与 Cache 的同名方法一一对应，一律作用于主缓存 ───────────

    /**
     * 读取主缓存的缓存值；规则同 {@link Cache#get(String, String)}。
     */
    public static <T> T get(String cacheName, String key) {
        return use().get(cacheName, key);
    }

    /**
     * 读取主缓存的缓存值；未命中时调用 {@code loader} 并按传入 TTL 写入，规则同
     * {@link Cache#get(String, String, Duration, Supplier)}。
     */
    public static <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader) {
        return use().get(cacheName, key, ttl, loader);
    }

    /**
     * 使用秒为单位读取并加载缓存；规则同 {@link Cache#get(String, String, int, Supplier)}。
     */
    public static <T> T get(String cacheName, String key, int ttlSeconds, Supplier<T> loader) {
        return use().get(cacheName, key, ttlSeconds, loader);
    }

    /**
     * 读取缓存；未命中时调用 {@code loader}，写入时使用主缓存的默认 TTL；规则同 {@link Cache#get(String, String, Supplier)}。
     */
    public static <T> T get(String cacheName, String key, Supplier<T> loader) {
        return use().get(cacheName, key, loader);
    }

    /**
     * 判断缓存项是否存在且未过期；规则同 {@link Cache#exists(String, String)}。
     */
    public static boolean exists(String cacheName, String key) {
        return use().exists(cacheName, key);
    }

    /**
     * 写入带有效期的缓存值；规则同 {@link Cache#put(String, String, Object, Duration)}。
     */
    public static void put(String cacheName, String key, Object value, Duration ttl) {
        use().put(cacheName, key, value, ttl);
    }

    /**
     * 使用秒为单位写入缓存值；规则同 {@link Cache#put(String, String, Object, int)}。
     */
    public static void put(String cacheName, String key, Object value, int ttlSeconds) {
        use().put(cacheName, key, value, ttlSeconds);
    }

    /**
     * 写入缓存值，有效期使用主缓存的默认 TTL；规则同 {@link Cache#put(String, String, Object)}。
     */
    public static void put(String cacheName, String key, Object value) {
        use().put(cacheName, key, value);
    }

    /**
     * 当缓存项不存在或已过期时写入缓存值；规则同 {@link Cache#putIfAbsent(String, String, Object, Duration)}。
     */
    public static boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
        return use().putIfAbsent(cacheName, key, value, ttl);
    }

    /**
     * 使用秒为单位，在缓存项不存在或已过期时写入缓存值；规则同 {@link Cache#putIfAbsent(String, String, Object, int)}。
     */
    public static boolean putIfAbsent(String cacheName, String key, Object value, int ttlSeconds) {
        return use().putIfAbsent(cacheName, key, value, ttlSeconds);
    }

    /**
     * 当缓存项不存在或已过期时写入缓存值，有效期使用主缓存的默认 TTL；规则同 {@link Cache#putIfAbsent(String, String, Object)}。
     */
    public static boolean putIfAbsent(String cacheName, String key, Object value) {
        return use().putIfAbsent(cacheName, key, value);
    }

    /**
     * 重设已有缓存项的剩余有效期；规则同 {@link Cache#expire(String, String, Duration)}。
     */
    public static boolean expire(String cacheName, String key, Duration ttl) {
        return use().expire(cacheName, key, ttl);
    }

    /**
     * 使用秒为单位重设已有缓存项的剩余有效期；规则同 {@link Cache#expire(String, String, int)}。
     */
    public static boolean expire(String cacheName, String key, int ttlSeconds) {
        return use().expire(cacheName, key, ttlSeconds);
    }

    /**
     * 重设已有缓存项的剩余有效期为主缓存的默认 TTL；规则同 {@link Cache#expire(String, String)}。
     */
    public static boolean expire(String cacheName, String key) {
        return use().expire(cacheName, key);
    }

    /**
     * 删除单个缓存项；规则同 {@link Cache#remove(String, String)}。
     */
    public static void remove(String cacheName, String key) {
        use().remove(cacheName, key);
    }

    /**
     * 删除该缓存名下的全部缓存项；规则同 {@link Cache#clear(String)}。
     */
    public static void clear(String cacheName) {
        use().clear(cacheName);
    }

    /**
     * 返回主缓存中缓存项的剩余有效期；规则同 {@link Cache#getTtl(String, String)}。
     */
    public static Duration getTtl(String cacheName, String key) {
        return use().getTtl(cacheName, key);
    }

    /**
     * 返回主缓存中该缓存名下按匹配类型筛选并分页后的键集合；规则同
     * {@link Cache#keys(String, int, int, String, CacheKeyMatchType)}。
     */
    public static List<String> keys(String cacheName, int offset, int limit,
                                    String pattern, CacheKeyMatchType matchType) {
        return use().keys(cacheName, offset, limit, pattern, matchType);
    }

    /**
     * 返回主缓存中该缓存名下的键分页，不做筛选；规则同 {@link Cache#keys(String, int, int)}。
     */
    public static List<String> keys(String cacheName, int offset, int limit) {
        return use().keys(cacheName, offset, limit);
    }

    /**
     * 返回主缓存中历史上被写入过的缓存名集合；规则同 {@link Cache#cacheNames()}。
     */
    public static Set<String> cacheNames() {
        return use().cacheNames();
    }

    /**
     * 返回主缓存的默认 TTL；规则同 {@link Cache#ttl()}。
     */
    public static Duration ttl() {
        return use().ttl();
    }

    /**
     * 返回主缓存是否缓存 {@code null} 值；规则同 {@link Cache#cacheNull()}。
     */
    public static boolean cacheNull() {
        return use().cacheNull();
    }

}

