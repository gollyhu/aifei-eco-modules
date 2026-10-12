/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.caffeine.CaffeineCacheProvider;
import cn.hg.aifei.cache.redis.RedisCacheProvider;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 后端 provider 注册表：{@code type} 到复用的无状态 provider 的映射。
 */
public final class CacheProviderRegistry {

    /**
     * 类型标识到无状态 provider 的映射；保持注册顺序以便异常消息稳定。
     */
    private static final Map<String, CacheProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        PROVIDERS.put("jedis", new RedisCacheProvider());
        PROVIDERS.put("caffeine", new CaffeineCacheProvider());
    }

    /**
     * 注册一个后端 provider；同类型重复注册即失败。
     */
    public void register(String type, CacheProvider provider) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("provider type can not be blank");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider can not be null");
        }
        String normalized = type.trim();
        if (PROVIDERS.containsKey(normalized)) {
            throw new IllegalStateException("cache provider already registered for type: " + normalized);
        }
        PROVIDERS.put(normalized, provider);
    }

    /**
     * 取指定类型复用的无状态 provider；调用方应紧接着调 {@link CacheProvider#buildCache}。
     */
    public CacheProvider getProvider(String type) {
        CacheProvider provider = PROVIDERS.get(type);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "unsupported cache type: " + type + ", supported types: " + types()
            );
        }
        return provider;
    }

    /**
     * 返回已注册的类型集合，供配置校验的类型白名单派生使用。
     */
    public Set<String> types() {
        return Collections.unmodifiableSet(PROVIDERS.keySet());
    }
}
