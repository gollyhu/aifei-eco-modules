/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存键拼装 + TTL 判定 + 全清的唯一定义点。
 *
 * <p>db / kv 来源按 key 懒加载并本地缓存；TTL≤0 直接回源（不缓存）；空串是合法命中、
 * {@code null} 是未命中哨兵，二者分别缓存，绝不把空串当未命中而重复回源。</p>
 */
public final class CacheLayer {

    /**
     * 缓存未命中时的取值器（函数式接口，JDK 自带类型，无第三方依赖）。
     */
    public interface Loader {
        /**
         * @return 命中的译文（可为空串）；无译文返回 {@code null}
         */
        String load();
    }

    /**
     * 缓存项：value 可为 {@code null}（表示已缓存的「未命中」），以对象存在性区分「未缓存」。
     */
    private static final class Entry {
        private final String value;
        private final long expireAt;

        private Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private final long ttlMillis;
    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<String, Entry>();

    /**
     * @param ttlMillis 缓存 TTL（毫秒）；{@code ≤ 0} 表示不缓存
     */
    public CacheLayer(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /**
     * 取缓存；命中（含缓存的未命中哨兵且未过期）直接返回，否则回源并按 TTL 写入。
     *
     * @param key       文案 key
     * @param localeTag 已规范化的 locale tag
     * @param loader    回源取值器，不得为 {@code null}
     * @return 译文（可为空串）；无译文返回 {@code null}
     */
    public String get(String key, String localeTag, Loader loader) {
        if (ttlMillis <= 0) {
            return loader.load();
        }
        String cacheKey = localeTag + '\u0001' + key;
        long now = System.currentTimeMillis();
        Entry cached = cache.get(cacheKey);
        if (cached != null && now < cached.expireAt) {
            return cached.value;
        }
        String loaded = loader.load();
        cache.put(cacheKey, new Entry(loaded, now + ttlMillis));
        return loaded;
    }

    /**
     * 全清缓存（含未命中哨兵），供 {@code I18nKit.refresh()} 显式刷新；幂等。
     */
    public void clearAll() {
        cache.clear();
    }

    /**
     * 仅用于诊断：当前缓存项数（不参与业务语义）。
     *
     * @return 缓存项数
     */
    int size() {
        return cache.size();
    }
}
