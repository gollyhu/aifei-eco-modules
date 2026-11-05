/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.redis;

import cn.hg.aifei.cache.internal.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 条件操作的离线用例：两条 Lua 常量、期望值字节装配与故障分类，不依赖 Redis。
 *
 * <p>「一次调用只发一条 EVAL」与真实的误删 / 误续期防护由 {@link RedisCacheIntegrationTest}
 * 覆盖；本类补上默认构建能跑的那一层。</p>
 */
public class RedisCacheCasTest {

    /**
     * 两条 Lua 常量各自包含 GET、字节比较与 DEL / PEXPIRE，且用 {@code redis.call} 让脚本错误与
     * 存储异常原样上抛。
     */
    @Test
    public void shouldKeepSingleRoundTripScripts() {
        List<String> scripts = casScripts();
        assertEquals("条件操作脚本应为 2 条", 2, scripts.size());

        String removeScript = null;
        String expireScript = null;
        for (String script : scripts) {
            if (script.contains("DEL")) {
                removeScript = script;
            }
            if (script.contains("PEXPIRE")) {
                expireScript = script;
            }
        }
        assertNotNull("缺少删除脚本", removeScript);
        assertNotNull("缺少续期脚本", expireScript);

        for (String script : new String[] {removeScript, expireScript}) {
            assertTrue("脚本必须先读当前值: " + script, script.contains("GET"));
            assertTrue("脚本必须按字节比较: " + script, script.contains("=="));
            assertTrue("脚本必须用 redis.call: " + script, script.contains("redis.call"));
        }
    }

    /**
     * 期望值字节装配点只经 codec 序列化：与写入时的字节一致，且不等同于字符串字节。
     */
    @Test
    public void shouldAssembleExpectedValueAsCodecBytes() {
        FuryRedisValueCodec codec = new FuryRedisValueCodec();
        String expected = "token-\u4e2d\u6587-\u0000-end";

        byte[] assembled = RedisCache.casExpectedBytes(codec, expected);

        assertArrayEquals("必须等于写入时的 codec 字节", codec.serialize(expected), assembled);
        assertFalse("不得经字符串中转",
                Arrays.equals(expected.getBytes(StandardCharsets.UTF_8), assembled));
    }

    /**
     * 参数校验先于能力检查与存储命令：不可达实例上的非法参数抛 {@code IllegalArgumentException}，
     * 而不是连接异常；能力声明本身不发起命令。
     */
    @Test
    public void shouldValidateArgumentsBeforeSendingCommands() {
        RedisCache cache = new RedisCache(unreachableConfig(), "cas-ns", Config.DEFAULT_TTL, false);
        Duration ttl = Duration.ofSeconds(30);
        try {
            assertTrue("jedis 后端支持条件操作", cache.supportsCas());

            assertEquals("expectedValue can not be null",
                    assertThrows(IllegalArgumentException.class,
                            () -> cache.removeIfEquals("lock", "1", null)).getMessage());
            assertThrows(IllegalArgumentException.class, () -> cache.removeIfEquals(null, "1", "token"));
            assertThrows(IllegalArgumentException.class, () -> cache.removeIfEquals(" ", "1", "token"));
            assertThrows(IllegalArgumentException.class, () -> cache.removeIfEquals("lock:sub", "1", "token"));
            assertThrows(IllegalArgumentException.class, () -> cache.removeIfEquals("lock", " ", "token"));

            assertEquals("expectedValue can not be null",
                    assertThrows(IllegalArgumentException.class,
                            () -> cache.expireIfEquals("lock", "1", null, ttl)).getMessage());
            assertThrows(IllegalArgumentException.class, () -> cache.expireIfEquals("lock", "1", "token", null));
            assertThrows(IllegalArgumentException.class,
                    () -> cache.expireIfEquals("lock", "1", "token", Duration.ZERO));
        } finally {
            cache.close();
        }
    }

    /**
     * 故障不吞：不可达地址上调用两个条件操作必须抛运行时异常，且不是 {@code false}。
     *
     * <p>若客户端在构造期即建连，「构造或调用任一阶段抛异常」同样合格。</p>
     */
    @Test
    public void shouldPropagateStorageFailureInsteadOfReturningFalse() {
        RedisCache cache;
        try {
            cache = new RedisCache(unreachableConfig(), "cas-fail", Config.DEFAULT_TTL, false);
        } catch (RuntimeException e) {
            return;
        }
        try {
            Duration ttl = Duration.ofSeconds(30);
            assertStorageFailure(() -> cache.removeIfEquals("lock", "1", "token"));
            assertStorageFailure(() -> cache.expireIfEquals("lock", "1", "token", ttl));
        } finally {
            cache.close();
        }
    }

    private static void assertStorageFailure(Runnable call) {
        try {
            call.run();
            fail("存储故障必须抛运行时异常，不得返回 false");
        } catch (UnsupportedOperationException e) {
            fail("jedis 后端不得抛 UnsupportedOperationException");
        } catch (IllegalArgumentException e) {
            fail("参数合法时不得抛 IllegalArgumentException: " + e);
        } catch (RuntimeException expected) {
            assertNotNull(expected);
        }
    }

    /**
     * 固定的不可达地址：不依赖主机名解析，避免 DNS 超时让用例耗时不稳。
     */
    private static RedisConfig unreachableConfig() {
        return new RedisConfig().host("127.0.0.1").port(1)
                .connectionTimeoutMillis(200).socketTimeoutMillis(200).maxWaitMillis(500L);
    }

    /**
     * 反射读取实现类内的 Lua 脚本常量，避免用例与字段名耦合。
     */
    private static List<String> casScripts() {
        List<String> scripts = new ArrayList<>();
        for (Field field : RedisCache.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() != String.class || !Modifier.isStatic(modifiers)
                    || !Modifier.isFinal(modifiers)) {
                continue;
            }
            field.setAccessible(true);
            try {
                String value = (String) field.get(null);
                if (value != null && value.contains("redis.call(")) {
                    scripts.add(value);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return scripts;
    }
}
