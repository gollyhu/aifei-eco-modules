/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

import com.github.benmanes.caffeine.cache.Expiry;

/**
 * 保留每个计数项单独设置的有效期。
 */
final class CaffeineCounterExpiry implements Expiry<CaffeineCacheKey, Long> {

    static final CaffeineCounterExpiry INSTANCE = new CaffeineCounterExpiry();

    private CaffeineCounterExpiry() {
    }

    /**
     * 新建计数项时先使用无限期，实际 TTL 由写入策略设置。
     */
    @Override
    public long expireAfterCreate(CaffeineCacheKey key, Long value, long currentTime) {
        return Long.MAX_VALUE;
    }

    /**
     * 更新计数项时保留当前有效期。
     */
    @Override
    public long expireAfterUpdate(CaffeineCacheKey key, Long value, long currentTime, long currentDuration) {
        return currentDuration;
    }

    /**
     * 读取计数项时不延长有效期。
     */
    @Override
    public long expireAfterRead(CaffeineCacheKey key, Long value, long currentTime, long currentDuration) {
        return currentDuration;
    }
}
