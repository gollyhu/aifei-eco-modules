/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken.dao;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKeyMatchType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 持久层测试共用的内存缓存桩。
 *
 * <p>只覆盖测试所需能力：不模拟过期淘汰（已写入项一律可见），{@code keys} 复刻
 * aifei-cache 的「筛选 + 字典序排序 + 分页」语义。这样测试不需要 Redis 或 aifei-cache 插件。</p>
 */
final class InMemoryCacheStub implements Cache {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Duration> ttls = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String cacheName, String key) {
        return (T) values.get(key);
    }

    @Override
    public boolean exists(String cacheName, String key) {
        return values.containsKey(key);
    }

    @Override
    public void put(String cacheName, String key, Object value, Duration ttl) {
        values.put(key, value);
        ttls.put(key, ttl);
    }

    @Override
    public boolean putIfAbsent(String cacheName, String key, Object value, Duration ttl) {
        if (values.containsKey(key)) {
            return false;
        }
        put(cacheName, key, value, ttl);
        return true;
    }

    @Override
    public boolean expire(String cacheName, String key, Duration ttl) {
        if (!values.containsKey(key)) {
            return false;
        }
        ttls.put(key, ttl);
        return true;
    }

    @Override
    public void remove(String cacheName, String key) {
        values.remove(key);
        ttls.remove(key);
    }

    @Override
    public void clear(String cacheName) {
        values.clear();
        ttls.clear();
    }

    @Override
    public Duration getTtl(String cacheName, String key) {
        return values.containsKey(key) ? ttls.get(key) : null;
    }

    @Override
    public List<String> keys(String cacheName, int offset, int limit, String pattern,
                             CacheKeyMatchType matchType) {
        List<String> sorted = new ArrayList<>(values.keySet());
        Collections.sort(sorted);

        List<String> matched = new ArrayList<>();
        boolean filtering = pattern != null && !pattern.isEmpty();
        for (String key : sorted) {
            if (!filtering || matches(key, pattern, matchType)) {
                matched.add(key);
            }
        }

        if (offset >= matched.size()) {
            return new ArrayList<>();
        }
        int count = Math.min(limit, matched.size() - offset);
        return new ArrayList<>(matched.subList(offset, offset + count));
    }

    private static boolean matches(String key, String pattern, CacheKeyMatchType matchType) {
        switch (matchType) {
            case EQUALS:
                return key.equals(pattern);
            case STARTS_WITH:
                return key.startsWith(pattern);
            case ENDS_WITH:
                return key.endsWith(pattern);
            case CONTAINS:
            default:
                return key.contains(pattern);
        }
    }

    @Override
    public Set<String> cacheNames() {
        return Collections.singleton("satoken");
    }

    @Override
    public Duration ttl() {
        return Duration.ofSeconds(3600);
    }

    @Override
    public boolean cacheNull() {
        return false;
    }

    @Override
    public String namespace() {
        return "test";
    }
}
