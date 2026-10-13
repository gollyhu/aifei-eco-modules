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
 *
 * <p>支持两种装配形态：</p>
 * <ol>
 *   <li><b>纯缓存</b>：{@code new SaTokenDaoForCache(CacheUtil.use())}。登录态只存在于缓存，
 *       适配「登录态无需落库」的部署；多实例需缓存后端为 Redis。</li>
 *   <li><b>缓存加速 + 权威持久层</b>：{@code new SaTokenDaoForCache(CacheUtil.use(), delegate)}。
 *       委托层（例如消费方基于自有表实现的关系库持久层）为<b>权威</b>，缓存只做加速：
 *       读未命中回源并回填、写先落委托层再刷新缓存、超时以委托层为准。</li>
 * </ol>
 *
 * <p>委托层只以 {@link SaTokenDao} 接口的形态出现，本类不对其存储形态做任何假设，
 * 因此不会引入 JDBC 或消费方类型依赖（{@code dao} 包的分层边界与架构门禁保持成立）。
 * 委托层为 {@code null} 时，行为与纯缓存形态完全一致。</p>
 *
 * <p>装配方式（须在 {@code SaTokenAifeiPlugin} 启动之后）：</p>
 * <pre>
 * public void onStart() {
 *     SaManager.setSaTokenDao(new SaTokenDaoForCache(CacheUtil.use(), new MySaTokenDao()));
 * }
 * </pre>
 *
 * <p><b>生命周期</b>：本类不覆盖 {@code init()} 与 {@code destroy()}，因此<b>不会转发</b>给委托层。
 * Sa-Token 在没有官方 starter 的场景下不会自动调用 {@code SaTokenDao.init()}，
 * 委托层的初始化与释放须由消费方在装配处（或其自身插件中）自行处理。</p>
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
     * 权威持久层；为 {@code null} 表示纯缓存形态。
     *
     * <p>非空时：读未命中回源并回填缓存、写先落本层再刷新缓存、超时以本层为准。</p>
     */
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

    // ─────────── 字符串读写 ───────────

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

    // ─────────── 对象读写 ───────────

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

    // ─────────── SaSession 读写 ───────────

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

    // ─────────── 会话管理 ───────────

    /**
     * 按前缀与关键字搜索数据。
     *
     * <p>存在权威持久层时以委托层为准（其结果完整且权威，缓存可能已过期或只覆盖部分键）；
     * 纯缓存形态下的筛选与分页语义对齐 Sa-Token 官方实现：先按 {@code prefix} 与
     * {@code keyword} 过滤，{@code sortType} 为 {@code false} 时反转结果，最后按
     * {@code start} / {@code size} 截取（{@code size} 为 -1 表示取到末尾）。</p>
     */
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
     * 更新已存在项的值并保持其剩余有效期。字符串与对象更新共用此处（纯缓存形态）。
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
