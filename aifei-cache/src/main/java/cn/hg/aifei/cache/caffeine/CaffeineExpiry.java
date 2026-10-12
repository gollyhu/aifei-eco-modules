/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

import com.github.benmanes.caffeine.cache.Expiry;

/**
 * 保留每个缓存项单独设置的有效期。
 */
final class CaffeineExpiry implements Expiry<CaffeineCacheKey, Object> {

    static final CaffeineExpiry INSTANCE = new CaffeineExpiry();

    private CaffeineExpiry() {
    }

    /**
     * 新建条目时先使用无限期，实际 TTL 由写入策略设置。
     */
    @Override
    public long expireAfterCreate(CaffeineCacheKey key, Object value, long currentTime) {
        return Long.MAX_VALUE;
    }

    /**
     * 更新条目时保留当前有效期。
     */
    @Override
    public long expireAfterUpdate(CaffeineCacheKey key, Object value, long currentTime, long currentDuration) {
        return currentDuration;
    }

    /**
     * 读取条目时不延长有效期。
     */
    @Override
    public long expireAfterRead(CaffeineCacheKey key, Object value, long currentTime, long currentDuration) {
        return currentDuration;
    }
}
