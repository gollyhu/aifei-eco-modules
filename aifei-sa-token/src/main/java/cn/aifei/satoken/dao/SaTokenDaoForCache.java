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
 * 基于 aifei-cache 的 Sa-Token 持久层实现，用于登录态在多实例间共享。
 *
 * <p>把 Sa-Token 的扁平 key 整体当作 aifei-cache 的缓存键，全部数据落在固定缓存名
 * {@value #CACHE_NAME} 下；命名空间与后端（Caffeine / Redis）由消费方的 aifei-cache
 * 配置决定，本类不对存储形态做任何假设。</p>
 *
 * <p>装配方式（须在 {@code SaTokenAifeiPlugin} 启动之后）：</p>
 * <pre>
 * public void onStart() {
 *     SaManager.setSaTokenDao(new SaTokenDaoForCache(CacheUtil.use()));
 * }
 * </pre>
 *
 * <p>不覆盖 {@code init()} 与 {@code destroy()}：缓存实例的建立与释放由 aifei-cache 插件负责。</p>
 *
 * <p>本类所在的 {@code dao} 包是本模块唯一引用 aifei-cache 类型的地方；aifei-cache 为
 * {@code optional} 依赖，不使用本类时不会进入消费方运行时。</p>
 */
public class SaTokenDaoForCache implements SaTokenDao {

    /** 承载 Sa-Token 全部数据的固定缓存名；不含 ':'，满足 aifei-cache 的缓存名约束 */
    private static final String CACHE_NAME = "satoken";

    /** aifei-cache 允许的最长有效期（秒），同时用作「永久」的近似表示 */
    private static final long MAX_SECONDS = Integer.MAX_VALUE;

    /** Sa-Token 用 -1 表示永不过期，映射为 aifei-cache 的最长有效期 */
    private static final Duration FOREVER = Duration.ofSeconds(MAX_SECONDS);

    private final Cache cache;

    /**
     * 使用指定的缓存实例创建持久层。
     *
     * @param cache 缓存实例，通常取自主缓存 {@code CacheUtil.use()}；不能为空
     * @throws IllegalArgumentException cache 为空
     */
    public SaTokenDaoForCache(Cache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("cache can not be null.");
        }
        this.cache = cache;
    }

    // ─────────── 字符串读写 ───────────

    @Override
    public String get(String key) {
        return cache.get(CACHE_NAME, key);
    }

    @Override
    public void set(String key, String value, long timeout) {
        store(key, value, timeout);
    }

    @Override
    public void update(String key, String value) {
        rewrite(key, value);
    }

    @Override
    public void delete(String key) {
        cache.remove(CACHE_NAME, key);
    }

    @Override
    public long getTimeout(String key) {
        return remainSeconds(cache.getTtl(CACHE_NAME, key));
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        resetTtl(key, timeout);
    }

    // ─────────── 对象读写 ───────────

    @Override
    public Object getObject(String key) {
        return cache.get(CACHE_NAME, key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getObject(String key, Class<T> classType) {
        Object value = cache.get(CACHE_NAME, key);
        return classType == null ? (T) value : classType.cast(value);
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        store(key, object, timeout);
    }

    @Override
    public void updateObject(String key, Object object) {
        rewrite(key, object);
    }

    @Override
    public void deleteObject(String key) {
        cache.remove(CACHE_NAME, key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return remainSeconds(cache.getTtl(CACHE_NAME, key));
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        resetTtl(key, timeout);
    }

    // ─────────── SaSession 读写：复用对象读写 ───────────

    @Override
    public SaSession getSession(String sessionId) {
        return getObject(sessionId, SaSession.class);
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        setObject(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        updateObject(session.getId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        deleteObject(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return getObjectTimeout(sessionId);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        updateObjectTimeout(sessionId, timeout);
    }

    // ─────────── 会话管理 ───────────

    /**
     * 按前缀与关键字搜索数据。
     *
     * <p>筛选与分页语义对齐 Sa-Token 官方实现：先按 {@code prefix} 与 {@code keyword} 过滤，
     * {@code sortType} 为 {@code false} 时反转结果，最后按 {@code start} / {@code size} 截取
     * （{@code size} 为 -1 表示取到末尾）。</p>
     */
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
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

    /**
     * 截取指定区段。
     *
     * <p>{@code start} 小于 0 时按 0 处理；{@code size} 为 -1 时取到末尾，其余情况取
     * {@code start + size} 条；越界自动截断。</p>
     */
    private static List<String> page(List<String> list, int start, int size) {
        int from = Math.max(start, 0);
        int end = size == -1 ? list.size() : from + size;

        List<String> result = new ArrayList<>();
        for (int i = from; i < end && i < list.size(); i++) {
            result.add(list.get(i));
        }
        return result;
    }

    // ─────────── 写入与有效期换算 ───────────

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

    /**
     * 更新已存在项的值并保持其剩余有效期。字符串与对象更新共用此处。
     *
     * <p>aifei-cache 未提供「只改值、保留 TTL」的原子操作，故先读剩余有效期再写回；
     * 该项不存在或已过期时跳过写入。</p>
     */
    private void rewrite(String key, Object value) {
        Duration ttl = cache.getTtl(CACHE_NAME, key);
        if (ttl != null) {
            cache.put(CACHE_NAME, key, value, ttl);
        }
    }

    /**
     * 重设已存在项的剩余有效期。
     *
     * <p>0 或小于等于 {@link #NOT_VALUE_EXPIRE} 时不调整：该项按约定本就不该存在，
     * 此处不引入删除动作，以免与其他实例的并发写入互相干扰。</p>
     */
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
