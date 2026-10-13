/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.dao;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKeyMatchType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 aifei-cache 的 Sa-Token 持久层。
 */
public class SaTokenDaoForCache implements SaTokenDao {

    /** 承载 Sa-Token 全部数据的固定缓存名；不含 ':'，满足 aifei-cache 的缓存名约束 */
    private static final String CACHE_NAME = "satoken";

    /** aifei-cache 允许的最长有效期（秒），同时用作「永久」的近似表示 */
    private static final long MAX_SECONDS = Integer.MAX_VALUE;

    /** Sa-Token 用 -1 表示永不过期，映射为 aifei-cache 的最长有效期 */
    private static final Duration FOREVER = Duration.ofSeconds(MAX_SECONDS);

    private final Cache cache;

    /** 权威持久层；为 {@code null} 表示纯缓存形态 */
    private final SaTokenDao delegate;

    /**
     * 使用指定的缓存实例创建纯缓存持久层。
     *
     * @param cache 缓存实例，通常取自主缓存 {@code CacheUtil.use()}；不能为空
     * @throws IllegalArgumentException cache 为空
     */
    public SaTokenDaoForCache(Cache cache) {
        this(cache, null);
    }

    /**
     * 使用指定的缓存实例与权威持久层创建持久层。
     *
     * @param cache 缓存实例，通常取自主缓存 {@code CacheUtil.use()}；不能为空
     * @param delegate 权威持久层；为 {@code null} 时退化为纯缓存形态
     * @throws IllegalArgumentException cache 为空
     */
    public SaTokenDaoForCache(Cache cache, SaTokenDao delegate) {
        if (cache == null) {
            throw new IllegalArgumentException("cache can not be null.");
        }
        this.cache = cache;
        this.delegate = delegate;
    }

    @Override
    public String get(String key) {
        String hit = cache.get(CACHE_NAME, key);
        if (hit != null || delegate == null) {
            return hit;
        }
        String value = delegate.get(key);
        if (value != null) {
            store(key, value, delegate.getTimeout(key));
        }
        return value;
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (delegate != null) {
            delegate.set(key, value, timeout);
        }
        store(key, value, timeout);
    }

    @Override
    public void update(String key, String value) {
        if (delegate != null) {
            delegate.update(key, value);
            store(key, value, delegate.getTimeout(key));
            return;
        }
        rewrite(key, value);
    }

    @Override
    public void delete(String key) {
        if (delegate != null) {
            delegate.delete(key);
        }
        cache.remove(CACHE_NAME, key);
    }

    @Override
    public long getTimeout(String key) {
        return delegate != null ? delegate.getTimeout(key) : remainSeconds(cache.getTtl(CACHE_NAME, key));
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (delegate != null) {
            delegate.updateTimeout(key, timeout);
        }
        resetTtl(key, timeout);
    }

    @Override
    public Object getObject(String key) {
        Object hit = cache.get(CACHE_NAME, key);
        if (hit != null || delegate == null) {
            return hit;
        }
        Object value = delegate.getObject(key);
        if (value != null) {
            store(key, value, delegate.getObjectTimeout(key));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getObject(String key, Class<T> classType) {
        Object value = getObject(key);
        return classType == null ? (T) value : classType.cast(value);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        if (delegate != null) {
            delegate.setObject(key, object, timeout);
        }
        store(key, object, timeout);
    }

    @Override
    public void updateObject(String key, Object object) {
        if (delegate != null) {
            delegate.updateObject(key, object);
            store(key, object, delegate.getObjectTimeout(key));
            return;
        }
        rewrite(key, object);
    }

    @Override
    public void deleteObject(String key) {
        if (delegate != null) {
            delegate.deleteObject(key);
        }
        cache.remove(CACHE_NAME, key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return delegate != null ? delegate.getObjectTimeout(key) : remainSeconds(cache.getTtl(CACHE_NAME, key));
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        if (delegate != null) {
            delegate.updateObjectTimeout(key, timeout);
        }
        resetTtl(key, timeout);
    }

    @Override
    public SaSession getSession(String sessionId) {
        SaSession hit = cache.get(CACHE_NAME, sessionId);
        if (hit != null || delegate == null) {
            return hit;
        }
        SaSession session = delegate.getSession(sessionId);
        if (session != null) {
            store(sessionId, session, delegate.getSessionTimeout(sessionId));
        }
        return session;
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        if (delegate != null) {
            delegate.setSession(session, timeout);
        }
        store(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        if (delegate != null) {
            delegate.updateSession(session);
            store(session.getId(), session, delegate.getSessionTimeout(session.getId()));
            return;
        }
        rewrite(session.getId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        if (delegate != null) {
            delegate.deleteSession(sessionId);
        }
        cache.remove(CACHE_NAME, sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return delegate != null
                ? delegate.getSessionTimeout(sessionId)
                : remainSeconds(cache.getTtl(CACHE_NAME, sessionId));
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        if (delegate != null) {
            delegate.updateSessionTimeout(sessionId, timeout);
        }
        resetTtl(sessionId, timeout);
    }

    /** 按前缀与关键字搜索数据 */
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        if (delegate != null) {
            return delegate.searchData(prefix, keyword, start, size, sortType);
        }

        String keyPrefix = prefix == null ? "" : prefix;
        String keywordFilter = keyword == null ? "" : keyword;

        List<String> matched = new ArrayList<>();
        for (String key : cache.keys(CACHE_NAME, 0, Integer.MAX_VALUE, keyPrefix, CacheKeyMatchType.STARTS_WITH)) {
            if (key.contains(keywordFilter)) {
                matched.add(key);
            }
        }

        if (!sortType) {
            Collections.reverse(matched);
        }
        return page(matched, start, size);
    }

    /** 截取指定区段；{@code size} 为 -1 时取到末尾，越界自动截断 */
    private static List<String> page(List<String> list, int start, int size) {
        int from = Math.max(start, 0);
        int end = size == -1 ? list.size() : from + size;

        List<String> result = new ArrayList<>();
        for (int i = from; i < end && i < list.size(); i++) {
            result.add(list.get(i));
        }
        return result;
    }

    /**
     * 写入值并按 Sa-Token 的秒级超时约定换算有效期。字符串与对象写入共用此处。
     *
     * @param key 键
     * @param value 值
     * @param timeout 有效期（秒）：大于 0 为限时存储；{@link #NEVER_EXPIRE} 为永久；
     *                0 或小于等于 {@link #NOT_VALUE_EXPIRE} 时不存储且保持现状
     */
    private void store(String key, Object value, long timeout) {
        Duration ttl = ttlOf(timeout);
        if (ttl != null) {
            cache.put(CACHE_NAME, key, value, ttl);
        }
    }

    /** 更新已存在项的值并保持其剩余有效期（纯缓存形态） */
    private void rewrite(String key, Object value) {
        Duration ttl = cache.getTtl(CACHE_NAME, key);
        if (ttl != null) {
            cache.put(CACHE_NAME, key, value, ttl);
        }
    }

    /** 重设已存在项的剩余有效期 */
    private void resetTtl(String key, long timeout) {
        Duration ttl = ttlOf(timeout);
        if (ttl != null) {
            cache.expire(CACHE_NAME, key, ttl);
        }
    }

    /** 换算秒级超时为缓存 TTL；返回 null 表示按 Sa-Token 约定不应写入 */
    private static Duration ttlOf(long timeout) {
        if (timeout == NEVER_EXPIRE) {
            return FOREVER;
        }
        if (timeout <= 0) {
            return null;
        }
        return timeout >= MAX_SECONDS ? FOREVER : Duration.ofSeconds(timeout);
    }

    /** 把缓存的剩余有效期换算为 Sa-Token 的秒级表示 */
    private static long remainSeconds(Duration ttl) {
        if (ttl == null) {
            return NOT_VALUE_EXPIRE;
        }
        long seconds = ttl.getSeconds();
        if (seconds >= MAX_SECONDS) {
            return NEVER_EXPIRE;
        }
        // 剩余不足 1 秒但尚未过期，向上取整为 1，避免与「0 表示不存储」的约定混淆
        return seconds <= 0 ? 1 : seconds;
    }
}
