/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.caffeine;

/**
 * Caffeine 存储的组合键：命名空间 + 名称（缓存名或计数名）+ 业务键。
 */
final class CaffeineCacheKey {

    private final String namespace;

    private final String cacheName;

    private final String key;

    /**
     * 创建组合键。
     */
    CaffeineCacheKey(String namespace, String cacheName, String key) {
        this.namespace = namespace;
        this.cacheName = cacheName;
        this.key = key;
    }

    /**
     * 判断该键是否属于指定命名空间下的指定缓存名称。
     */
    boolean belongsTo(String namespace, String cacheName) {
        if (!this.namespace.equals(namespace)) {
            return false;
        }
        return this.cacheName.equals(cacheName);
    }

    /**
     * 返回业务键。
     */
    String key() {
        return key;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CaffeineCacheKey)) {
            return false;
        }
        CaffeineCacheKey other = (CaffeineCacheKey) object;
        return namespace.equals(other.namespace)
                && cacheName.equals(other.cacheName)
                && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        int result = namespace.hashCode();
        result = 31 * result + cacheName.hashCode();
        return 31 * result + key.hashCode();
    }

    @Override
    public String toString() {
        return "{namespace='" + namespace + "', cacheName='" + cacheName + "', key='" + key + "'}";
    }
}
