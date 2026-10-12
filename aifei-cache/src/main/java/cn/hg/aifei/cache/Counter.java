/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache;

import java.time.Duration;
import java.util.Set;

/**
 * 计数读写接口：业务代码只依赖本接口，不依赖 Caffeine 或 Redis 的具体实现，换后端只改配置。
 *
 * <p>计数器由缓存实例派生，与同源的 {@link Cache} 共用命名空间和默认 TTL：主计数器直接用
 * {@code @Inject} 注入，或用 {@code CounterUtil.use()} 取；配置了扩展实例时，用
 * {@code CounterUtil.use(实例名)} 按名字取。</p>
 *
 * <p><b>接口通用规则</b>：命名与空白校验、TTL 范围、返回值与名称集合语义为本接口与 {@link Cache}
 * 共用，统一写在 README 的「接口通用规则」一节；本接口没有 {@code cacheNull} 配置，也没有带
 * {@code loader} 的读取，计数溢出与非法计数值的异常只在下面各成员上说明。各方法只写摘要与自身
 * 差异点，不再复述共用规则。</p>
 */
public interface Counter {

    /**
     * 读取计数值。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @return 计数值；不存在、已过期或参数非法时 {@code null}（不返回 {@code 0}）
     * @throws IllegalStateException 计数项由外部直接写入、且没有 TTL 或不是合法整数（仅 jedis 后端）
     */
    Long get(String counterName, String key);

    /**
     * 增加计数值，固定窗口语义：首次创建时确定过期时间，后续命中只更新数值、不延长窗口。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttl         首次创建时的有效期
     * @return 更新后的计数值
     * @throws IllegalArgumentException 参数校验失败
     * @throws ArithmeticException 结果超出 {@code long} 范围
     */
    long increase(String counterName, String key, long step, Duration ttl);

    /**
     * 增加计数值，每次命中都把剩余 TTL 重置为传入值（闲置过期语义）。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttl         每次命中的新有效期
     * @return 更新后的计数值
     * @throws IllegalArgumentException 参数校验失败
     * @throws ArithmeticException 结果超出 {@code long} 范围
     */
    long increaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);

    /**
     * 减少计数值，固定窗口语义。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttl         首次创建时的有效期
     * @return 更新后的计数值
     * @throws IllegalArgumentException 参数校验失败
     * @throws ArithmeticException 结果超出 {@code long} 范围
     */
    long decrease(String counterName, String key, long step, Duration ttl);

    /**
     * 减少计数值，每次命中都把剩余 TTL 重置为传入值（闲置过期语义）。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttl         每次命中的新有效期
     * @return 更新后的计数值
     * @throws IllegalArgumentException 参数校验失败
     * @throws ArithmeticException 结果超出 {@code long} 范围
     */
    long decreaseAndRefreshTtl(String counterName, String key, long step, Duration ttl);

    /**
     * 删除单个计数项；计数项不存在或已过期时不执行任何操作，也不改变 {@link #counterNames()}。
     *
     * @param counterName 计数名称
     * @param key         计数键
     */
    void remove(String counterName, String key);

    /**
     * 清空该计数名称下的全部计数项，并把该计数名称从 {@link #counterNames()} 中移除；
     * 清理范围与非原子性见「接口通用规则」。
     *
     * @param counterName 计数名称
     * @throws IllegalArgumentException 参数校验失败
     */
    void clear(String counterName);

    /**
     * 返回曾被创建过的计数名称集合；增减规则与可见范围见「接口通用规则」。
     *
     * @return 曾被创建过的计数名称集合
     */
    Set<String> counterNames();

    /**
     * 返回该实例的默认有效期，即派生它的缓存实例的 {@code cache.ttlMillis} 取值；省略 TTL 的计数
     * 方法用的就是它。
     *
     * @return 该实例的默认有效期
     */
    Duration ttl();

    /**
     * 返回该实例的命名空间，与派生它的缓存实例相同。
     *
     * @return 该实例的命名空间
     */
    String namespace();

    // ─────────── default 方法：秒重载统一委托到 Duration 版本 ───────────

    /**
     * 使用秒为单位增加计数值。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttlSeconds  有效秒数
     * @return 更新后的计数值
     */
    default long increase(String counterName, String key, long step, int ttlSeconds) {
        return increase(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 使用秒为单位增加计数值并刷新 TTL。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttlSeconds  新的有效秒数
     * @return 更新后的计数值
     */
    default long increaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds) {
        return increaseAndRefreshTtl(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 使用秒为单位减少计数值。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttlSeconds  有效秒数
     * @return 更新后的计数值
     */
    default long decrease(String counterName, String key, long step, int ttlSeconds) {
        return decrease(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    /**
     * 使用秒为单位减少计数值并刷新 TTL。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @param ttlSeconds  新的有效秒数
     * @return 更新后的计数值
     */
    default long decreaseAndRefreshTtl(String counterName, String key, long step, int ttlSeconds) {
        return decreaseAndRefreshTtl(counterName, key, step, Duration.ofSeconds(ttlSeconds));
    }

    // ─────────── default 方法：省略 TTL 重载统一委托到本实例 ttl() ───────────

    /**
     * 增加计数值，有效期取该实例的默认 TTL。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @return 更新后的计数值
     */
    default long increase(String counterName, String key, long step) {
        return increase(counterName, key, step, ttl());
    }

    /**
     * 增加计数值并刷新 TTL，有效期取该实例的默认 TTL。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @return 更新后的计数值
     */
    default long increaseAndRefreshTtl(String counterName, String key, long step) {
        return increaseAndRefreshTtl(counterName, key, step, ttl());
    }

    /**
     * 减少计数值，有效期取该实例的默认 TTL。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @return 更新后的计数值
     */
    default long decrease(String counterName, String key, long step) {
        return decrease(counterName, key, step, ttl());
    }

    /**
     * 减少计数值并刷新 TTL，有效期取该实例的默认 TTL。
     *
     * @param counterName 计数名称
     * @param key         计数键
     * @param step        步长
     * @return 更新后的计数值
     */
    default long decreaseAndRefreshTtl(String counterName, String key, long step) {
        return decreaseAndRefreshTtl(counterName, key, step, ttl());
    }
}
