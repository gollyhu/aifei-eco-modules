/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache;

import cn.hg.aifei.cache.internal.CacheValidator;
import cn.hg.aifei.cache.internal.SingleFlight;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 缓存读写接口：业务代码只依赖本接口，不依赖 Caffeine 或 Redis 的具体实现，换后端只改配置。
 *
 * <p>一个应用可以同时持有多个缓存实例：主缓存直接用 {@code @Inject} 注入，或用
 * {@code CacheUtil.use()} 取；配置了扩展实例时，用 {@code CacheUtil.use(实例名)} 按名字取。</p>
 *
 * <p><b>接口通用规则</b>：命名与空白校验、TTL 范围、返回值与名称集合语义为本接口与 {@link Counter}
 * 共用，统一写在 README 的「接口通用规则」一节；{@code cacheNull} 语义与带 {@code loader} 的读取
 * 约束为本接口专有，异常契约两侧也不同（差异写在各成员的异常说明里）。下面各方法只写摘要与自身
 * 差异点，不再复述共用规则。</p>
 */
public interface Cache {

    /**
     * 读取缓存值。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param <T> 期望的值类型
     * @return 缓存值；不存在、已过期或参数非法时返回 {@code null}
     */
    <T> T get(String cacheName, String key);

    /**
     * 判断缓存项是否存在且未过期。
     *
     * <p>本方法看的是「缓存里有没有这一项」，与值本身是不是 {@code null} 无关，因此不能用
     * {@code get(cacheName, key) != null} 代替；详见「接口通用规则」的 {@code cacheNull} 一节。</p>
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @return 存在且未过期时 {@code true}；否则 {@code false}
     */
    boolean exists(String cacheName, String key);

    /**
     * 写入带有效期的缓存值；{@code value} 为 {@code null} 时的行为见 {@link #cacheNull()} 与
     * 「接口通用规则」的 {@code cacheNull} 一节。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 有效期
     * @throws IllegalArgumentException 参数校验失败
     */
    void put(String cacheName, String key, Object value, Duration ttl);

    /**
     * 当缓存项不存在或已过期时写入缓存值。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param value 缓存值
     * @param ttl 有效期
     * @return 写入成功时 {@code true}；已存在且未过期、或该值不写入时 {@code false}
     * @throws IllegalArgumentException 参数校验失败
     */
    boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl);

    /**
     * 重设已存在且未过期缓存项的剩余有效期；只改过期时间，不读取、不修改缓存值。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param ttl 新的剩余有效期
     * @return 重设成功时 {@code true}；不存在或已过期时 {@code false}
     * @throws IllegalArgumentException 参数校验失败
     */
    boolean expire(String cacheName, String key, Duration ttl);

    /**
     * 删除单个缓存项；缓存项不存在或已过期时不执行任何操作，也不改变 {@link #cacheNames()}。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     */
    void remove(String cacheName, String key);

    /**
     * 删除该缓存名下的全部缓存项，并把该缓存名从 {@link #cacheNames()} 中移除；
     * 清理范围与非原子性见「接口通用规则」。
     *
     * @param cacheName 缓存名
     * @throws IllegalArgumentException 参数校验失败
     */
    void clear(String cacheName);

    /**
     * 返回缓存项的剩余有效期。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @return 剩余有效期；不存在、已过期或参数非法时 {@code null}
     */
    Duration getTtl(String cacheName, String key);

    /**
     * 返回该缓存名下按 {@code pattern} 筛选后的键分页，按字典序升序排列；筛选、排序、分页与
     * 一致性规则见「接口通用规则」。
     *
     * <p>结果为弱一致快照，扫描期间的并发写入可能使相邻两次分页重叠或遗漏；Redis 后端为全量
     * 扫描，键量大时开销显著。</p>
     *
     * @param cacheName 缓存名
     * @param offset 跳过的键条数，从 0 开始
     * @param limit 返回的键条数上限
     * @param pattern 匹配串；{@code null} 或空串时不筛选
     * @param matchType 匹配类型；{@code pattern} 为 {@code null} 或空串时不参与判定
     * @return 键列表，按字典序升序；不为 {@code null}
     * @throws IllegalArgumentException 参数校验失败
     */
    List<String> keys(String cacheName, int offset, int limit, String pattern, CacheKeyMatchType matchType);

    /**
     * 返回历史上被写入过的缓存名集合，用于排查问题；增减规则与可见范围见「接口通用规则」。
     *
     * @return 被写入过的缓存名集合
     */
    Set<String> cacheNames();

    /**
     * 返回该实例的默认有效期；省略 TTL 的方法用的就是它。
     *
     * <p>取值为本实例配置段的 {@code ttlMillis}：主缓存为 {@code cache.ttlMillis}，扩展实例为
     * {@code cache.<name>.ttlMillis}（未配置时继承主缓存）。</p>
     *
     * @return 该实例的默认有效期
     */
    Duration ttl();

    /**
     * 返回该实例是否把 {@code null} 也当作可缓存的值。
     *
     * <p>取值为本实例配置段的 {@code cacheNull}：主缓存为 {@code cache.cacheNull}，扩展实例为
     * {@code cache.<name>.cacheNull}（未配置时继承主缓存）。</p>
     *
     * @return 该实例是否缓存 {@code null} 值
     */
    boolean cacheNull();

    /**
     * 返回该实例的命名空间：该实例所有键在存储中的统一前缀，用来区分不同应用与实例。
     *
     * <p>取值为本实例配置段的 {@code namespace}：主缓存为 {@code cache.namespace}，扩展实例为
     * {@code cache.<name>.namespace}；<b>不继承主缓存</b>，两侧缺省值相同时会互相覆盖。</p>
     *
     * @return 该实例的命名空间
     */
    String namespace();

    // ─────────── default 方法：省略 TTL 的重载、秒重载与带 loader 的读取 ───────────

    /**
     * 写入缓存值，有效期取该实例的默认 TTL。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param value 缓存值
     */
    default void put(String cacheName, String key, Object value) {
        this.put(cacheName, key, value, this.ttl());
    }

    /**
     * 当缓存项不存在或已过期时写入缓存值，有效期取该实例的默认 TTL。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param value 缓存值
     * @return 写入成功时 {@code true}；已存在且未过期、或该值不写入时 {@code false}
     */
    default boolean putIfAbsent(String cacheName, String key, Object value) {
        return this.putIfAbsent(cacheName, key, value, this.ttl());
    }

    /**
     * 重设已存在且未过期缓存项的剩余有效期，取该实例的默认 TTL。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @return 成功重设时 {@code true}；不存在或已过期时 {@code false}
     */
    default boolean expire(String cacheName, String key) {
        return this.expire(cacheName, key, this.ttl());
    }

    /**
     * 读取缓存；未命中时调用 {@code loader} 并按传入 TTL 写入。
     *
     * <p>与不带 {@code loader} 的 {@link #get(String, String)} 的差别：参数非法或 {@code loader}
     * 为 {@code null} 时它抛异常而不是返回 {@code null}，且不会调用 {@code loader}；命中否定结果时
     * 同样直接返回 {@code null}，既不调用 {@code loader}，也不刷新它的有效期。并发只装载一次、
     * 使用约束等规则见「接口通用规则」的「带 {@code loader} 的读取」。</p>
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param ttl 加载值的有效期
     * @param loader 未命中时调用的加载器
     * @param <T> 期望的值类型
     * @return 缓存值或加载值
     * @throws IllegalArgumentException 参数校验失败，或 {@code loader} 为 {@code null}
     */
    default <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("loader can not be null");
        }
        CacheValidator.requireCacheName(cacheName);
        CacheValidator.requireKey(key);
        long ttlMillis = CacheValidator.requireTtl(ttl);

        T value = get(cacheName, key);
        if (value != null) {
            return value;
        }

        // 命中否定结果（存的是哨兵）：直接返回 null，不回源 —— 回源写入会重置它的有效期。
        if (exists(cacheName, key)) {
            return null;
        }

        return SingleFlight.load(this, cacheName, key, ttlMillis, loader);
    }

    /**
     * 读取缓存；未命中时调用 {@code loader} 并写入，使用该实例的默认 TTL。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param loader 未命中时调用的加载器
     * @param <T> 期望的值类型
     * @return 缓存值或加载值
     */
    default <T> T get(String cacheName, String key, Supplier<T> loader) {
        return get(cacheName, key, ttl(), loader);
    }

    /**
     * 使用秒为单位读取并加载缓存。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param ttlSeconds 加载值的有效秒数
     * @param loader 未命中时调用的加载器
     * @param <T> 期望的值类型
     * @return 缓存值或加载值
     */
    default <T> T get(String cacheName, String key, int ttlSeconds, Supplier<T> loader) {
        return get(cacheName, key, Duration.ofSeconds(ttlSeconds), loader);
    }

    /**
     * 使用秒为单位写入缓存值。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 有效秒数
     */
    default void put(String cacheName, String key, Object value, int ttlSeconds) {
        put(cacheName, key, value, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 使用秒为单位，在缓存项不存在或已过期时写入缓存值。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 有效秒数
     * @return 成功写入时 {@code true}
     */
    default boolean putIfAbsent(String cacheName, String key, Object value, int ttlSeconds) {
        return putIfAbsent(cacheName, key, value, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 使用秒为单位重设已有缓存项的剩余有效期。
     *
     * @param cacheName 缓存名
     * @param key 缓存键
     * @param ttlSeconds 新的剩余有效秒数
     * @return 成功重设时 {@code true}
     */
    default boolean expire(String cacheName, String key, int ttlSeconds) {
        return expire(cacheName, key, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 返回该缓存名下的键分页，不做筛选；规则同
     * {@link #keys(String, int, int, String, CacheKeyMatchType)}。
     *
     * @param cacheName 缓存名
     * @param offset 跳过的键条数，从 0 开始
     * @param limit 返回的键条数上限
     * @return 键列表，按字典序升序；不为 {@code null}
     */
    default List<String> keys(String cacheName, int offset, int limit) {
        return keys(cacheName, offset, limit, null, CacheKeyMatchType.CONTAINS);
    }

}
