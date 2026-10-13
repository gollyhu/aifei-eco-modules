/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.redis;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@code RedisConfig#uniqueName()} 的语义用例：唯一名必须按
 * <b>与 {@code createClient()} 完全相同的 URI 覆盖语义</b>计算。
 *
 * <p>本类位于 {@code cn.hg.aifei.cache.redis} 包，以便调用包级私有的 {@code uniqueName()}；纯配置对象，不建立任何连接。</p>
 */
public class RedisConfigUniqueNameTest {

    /**
     * 默认唯一名必须与 createClient() 的默认建连参数（127.0.0.1:6379/0）一致。
     */
    @Test
    public void shouldUseDefaults() {
        assertEquals("127.0.0.1:6379/0", new RedisConfig().uniqueName());
    }

    /**
     * 字段形式：host / port / database 逐项生效。
     */
    @Test
    public void shouldComposeFromExplicitFields() {
        assertEquals("10.0.0.1:6380/3",
                new RedisConfig().host("10.0.0.1").port(6380).database(3).uniqueName());
    }

    /**
     * URI 形式：host / port / database 全部取自 URI，<b>字段被忽略</b>。
     */
    @Test
    public void shouldPreferUriOverFields() {
        assertEquals("10.0.0.9:6381/5",
                new RedisConfig()
                        .uri("redis://10.0.0.9:6381/5")
                        .host("ignored-host")
                        .port(1)
                        .uniqueName());
    }

    /**
     * 显式 {@code database} 覆盖 URI 中的 db index（与 {@code createClient()} 的「显式 &gt; URI &gt; 0」一致）。
     */
    @Test
    public void shouldPreferExplicitDatabaseOverUri() {
        assertEquals("10.0.0.9:6381/7",
                new RedisConfig().uri("redis://10.0.0.9:6381/5").database(7).uniqueName());
    }

    /**
     * URI 不含 db index 时回落为 0。
     */
    @Test
    public void shouldFallBackToZeroDatabaseWhenUriHasNoIndex() {
        assertEquals("10.0.0.9:6381/0",
                new RedisConfig().uri("redis://10.0.0.9:6381").uniqueName());
    }
}
