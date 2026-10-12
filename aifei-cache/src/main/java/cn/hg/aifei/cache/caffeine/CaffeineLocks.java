/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.caffeine;

/**
 * 分段锁：同一缓存项的操作共用一把锁，不同项按哈希分散到不同锁。
 */
final class CaffeineLocks {

    /**
     * 使用位掩码选锁，长度必须为 2 的幂。
     */
    private final Object[] locks = new Object[128];

    CaffeineLocks() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    Object forKey(CaffeineCacheKey key) {
        return locks[key.hashCode() & (locks.length - 1)];
    }
}
