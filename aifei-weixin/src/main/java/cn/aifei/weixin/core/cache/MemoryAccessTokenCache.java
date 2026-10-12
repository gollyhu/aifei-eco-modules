/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 token 缓存。读取时判过期即视为未命中并清除。
 */
public class MemoryAccessTokenCache implements AccessTokenCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expireAtMillis) {
            entries.remove(key);
            return null;
        }
        return entry.token;
    }

    @Override
    public void set(String key, String token, long expireAtMillis) {
        entries.put(key, new Entry(token, expireAtMillis));
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }

    private static class Entry {
        final String token;
        final long expireAtMillis;

        Entry(String token, long expireAtMillis) {
            this.token = token;
            this.expireAtMillis = expireAtMillis;
        }
    }
}
