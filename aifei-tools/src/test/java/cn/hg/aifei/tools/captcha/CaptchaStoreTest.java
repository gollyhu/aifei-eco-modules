/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.tools.captcha.internal.CaptchaStore;
import cn.hg.aifei.tools.captcha.internal.ClusterCaptchaStore;
import cn.hg.aifei.tools.captcha.internal.LruCaptchaStore;
import cn.hg.aifei.cache.internal.CounterFactory;
import cn.hg.aifei.cache.internal.Config;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 验证码存储测试（设计 §3.2 / §8-E2 / §9）。
 *
 * <p>覆盖：Lru 容量淘汰、TTL、失败计数；三态（成功删 / 失败只计数 / 超限删）；
 * Cluster 存储用内存 Mock Cache 驱动，不连真实 Redis。</p>
 */
public class CaptchaStoreTest {

    @After
    public void clearCacheRegistry() {
        CacheManager.clear();
    }

    @Test
    public void lru_evictsLeastRecentlyUsed() {
        LruCaptchaStore store = new LruCaptchaStore(2, 5);
        long exp = System.currentTimeMillis() + 60000L;
        store.save("t1", "a1", exp);
        store.save("t2", "a2", exp);
        assertEquals("a1", store.consume("t1")); // 访问 t1，使其成为最近使用
        store.save("t3", "a3", exp);
        assertNull("最久未使用的 t2 应被淘汰", store.consume("t2"));
        assertEquals("a1", store.consume("t1"));
        assertEquals("a3", store.consume("t3"));
    }

    @Test
    public void lru_ttlExpired_returnsNullAndRemoves() {
        LruCaptchaStore store = new LruCaptchaStore(10, 5);
        store.save("expired", "a", System.currentTimeMillis() - 1L);
        assertNull("已过期的条目须视为未命中", store.consume("expired"));
        store.save("valid", "b", System.currentTimeMillis() + 60000L);
        assertEquals("b", store.consume("valid"));
    }

    @Test
    public void lru_successDeletes_failOnlyCounts_overLimitDeletes() {
        LruCaptchaStore store = new LruCaptchaStore(10, 2);
        long exp = System.currentTimeMillis() + 60000L;

        // 成功即删
        store.save("ok", "a", exp);
        assertEquals("a", store.consume("ok"));
        store.remove("ok");
        assertNull(store.consume("ok"));

        // 失败只计数，不消费
        store.save("fail", "b", exp);
        store.fail("fail");
        assertEquals("失败仅计数，token 仍有效", "b", store.consume("fail"));

        // 超限删除
        store.fail("fail"); // failures 达 2 == maxFailures
        assertNull("超限后须失效", store.consume("fail"));
    }

    @Test
    public void cluster_saveConsumeRemove_roundTrip() {
        ClusterCaptchaStore store = newClusterStore(5);
        long exp = System.currentTimeMillis() + 60000L;
        store.save("token", "1234", exp);
        assertEquals("1234", store.consume("token"));
        store.remove("token");
        assertNull(store.consume("token"));
    }

    @Test
    public void cluster_ttlExpired_returnsNull() {
        ClusterCaptchaStore store = newClusterStore(5);
        store.save("old", "9", System.currentTimeMillis() - 1L);
        assertNull("过期条目须视为未命中", store.consume("old"));
    }

    @Test
    public void cluster_threeStates_successFailOverLimit() {
        ClusterCaptchaStore store = newClusterStore(2);
        long exp = System.currentTimeMillis() + 60000L;

        store.save("ok", "a", exp);
        assertEquals("a", store.consume("ok"));
        store.remove("ok");
        assertNull(store.consume("ok"));

        store.save("bad", "b", exp);
        store.fail("bad");
        assertEquals("失败只计数", "b", store.consume("bad"));
        store.fail("bad"); // 达上限
        assertNull("超限须删除", store.consume("bad"));
    }

    @Test
    public void captchaStore_defaultRemoveIsEmpty() {
        // 默认方法契约：最小桩只需实现三方法即可编译，remove 默认空实现
        final Map<String, String> data = new ConcurrentHashMap<String, String>();
        CaptchaStore stub = new CaptchaStore() {
            public void save(String token, String answer, long expireAt) { data.put(token, answer); }
            public String consume(String token) { return data.get(token); }
            public void fail(String token) { data.remove(token); }
        };
        stub.save("t", "v", System.currentTimeMillis() + 1000L);
        stub.remove("t"); // 默认空实现，不应抛异常
        assertEquals("v", stub.consume("t"));
    }

    // ---------------------------------------------------------------- 工具

    private static ClusterCaptchaStore newClusterStore(int maxFailures) {
        Map<String, Object> data = new ConcurrentHashMap<String, Object>();
        Cache mock = mockCache(data);
        CacheManager.clear();
        CacheManager.register(Config.MAIN_INSTANCE_NAME, mock);
        return new ClusterCaptchaStore(maxFailures);
    }

    /** 用动态代理实现 Cache + CounterFactory，值存内存 Map，不连任何外部缓存。 */
    private static Cache mockCache(final Map<String, Object> data) {
        final Counter counter = (Counter) Proxy.newProxyInstance(
                Counter.class.getClassLoader(), new Class<?>[]{Counter.class},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        return defaultValue(method.getReturnType());
                    }
                });
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("get".equals(name)) {
                    return data.get(args[1]);
                }
                if ("put".equals(name)) {
                    data.put((String) args[1], args[2]);
                    return null;
                }
                if ("remove".equals(name)) {
                    data.remove(args[1]);
                    return null;
                }
                if ("clear".equals(name)) {
                    data.clear();
                    return null;
                }
                if ("exists".equals(name)) {
                    return data.containsKey(args[1]);
                }
                if ("createCounter".equals(name)) {
                    return counter;
                }
                if ("ttl".equals(name)) {
                    return Duration.ofSeconds(120);
                }
                if ("namespace".equals(name)) {
                    return "test";
                }
                if ("cacheNames".equals(name)) {
                    return new java.util.HashSet<String>();
                }
                return defaultValue(method.getReturnType());
            }
        };
        return (Cache) Proxy.newProxyInstance(
                Cache.class.getClassLoader(), new Class<?>[]{Cache.class, CounterFactory.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        if (type == int.class) {
            return Integer.valueOf(0);
        }
        if (type == short.class) {
            return Short.valueOf((short) 0);
        }
        if (type == byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (type == double.class) {
            return Double.valueOf(0D);
        }
        if (type == float.class) {
            return Float.valueOf(0F);
        }
        if (type == char.class) {
            return Character.valueOf('\0');
        }
        return null;
    }
}
