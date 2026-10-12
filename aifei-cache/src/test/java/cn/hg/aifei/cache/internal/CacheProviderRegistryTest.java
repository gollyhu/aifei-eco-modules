/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.TestSupport;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link CacheProviderRegistry} 的内置后端、注册通道、按类型复用与异常用例。
 *
 * <p>注册表是进程级静态的，{@link CacheProviderRegistry#register} 写入全局状态；因此本类中需要注册
 * 的用例都使用各自唯一的 {@code type} 名，避免用例之间相互污染。</p>
 */
public class CacheProviderRegistryTest {

    /**
     * 注册表<b>内置</b> {@code jedis} 与 {@code caffeine} 两个后端，无需外部注册。
     *
     * <p>只断言「包含」而不断言「恰好等于」：注册表是进程级静态的，同 JVM 内其它用例注册的类型会永久留在表中，断言相等会让本用例的通过与否取决于方法执行顺序。</p>
     */
    @Test
    public void shouldHaveBuiltInBackends() {
        CacheProviderRegistry registry = new CacheProviderRegistry();

        assertTrue(registry.types().containsAll(Arrays.asList("jedis", "caffeine")));
        assertEquals("jedis", registry.getProvider("jedis").type());
        assertEquals("caffeine", registry.getProvider("caffeine").type());
    }

    /**
     * {@code getProvider} 返回按类型复用的同一个 provider：多次取用得到同一对象。provider 无状态，
     * 配置在 {@link CacheProvider#buildCache} 内现读现用，因此复用是安全的。
     */
    @Test
    public void shouldReturnReusableProvider() {
        CacheProviderRegistry registry = new CacheProviderRegistry();

        assertSame(registry.getProvider("caffeine"), registry.getProvider("caffeine"));
        assertSame(registry.getProvider("jedis"), registry.getProvider("jedis"));
    }

    /**
     * 无状态（防回归）：同一个被复用的 provider 连续两次 {@code buildCache(...)}，各自按传入的
     * {@code prefix} 解析，产出互不影响的实例。
     *
     * <p>反例：provider 把解析结果存进自身字段、装配层分两步「先全部 configure 再逐个 build」，
     * 第二个实例的配置会覆盖第一个，同一后端的多实例互相串配置。本用例用「两个实例的 namespace
     * 必须各自正确」钉死该不变量。</p>
     */
    @Test
    public void shouldBuildIndependentCachesFromReusedProvider() {
        CacheProvider reusable = new CacheProviderRegistry().getProvider("caffeine");

        Cache first = reusable.buildCache("first", "cache.first",
                TestSupport.prop("cache.first.type", "caffeine", "cache.first.namespace", "ns-first"));
        Cache second = reusable.buildCache("second", "cache.second",
                TestSupport.prop("cache.second.type", "caffeine", "cache.second.namespace", "ns-second"));

        assertEquals("ns-first", first.namespace());
        assertEquals("ns-second", second.namespace());
    }

    /**
     * 空白 type 与 {@code null} provider 都必须失败。
     */
    @Test
    public void shouldRejectBlankTypeOrNullProvider() {
        CacheProviderRegistry registry = new CacheProviderRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(null, new StubProvider("reg-null-type")));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("  ", new StubProvider("reg-blank-type")));
        assertThrows(IllegalArgumentException.class, () -> registry.register("reg-null-provider", null));
    }

    /**
     * 同类型重复注册必须失败 —— 包括与<b>内置类型</b>冲突。
     */
    @Test
    public void shouldRejectDuplicateType() {
        CacheProviderRegistry registry = new CacheProviderRegistry();

        assertThrows(IllegalStateException.class,
                () -> registry.register("jedis", new StubProvider("jedis")));

        registry.register("reg-dup", new StubProvider("reg-dup"));
        assertThrows(IllegalStateException.class,
                () -> registry.register("reg-dup", new StubProvider("reg-dup")));
    }

    /**
     * 未知类型的异常消息必须列出<b>已注册类型</b> —— 使类型白名单只有一个定义点。
     */
    @Test
    public void shouldListSupportedTypesForUnknownType() {
        CacheProviderRegistry registry = new CacheProviderRegistry();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> registry.getProvider("ghost"));
        assertTrue(exception.getMessage().contains("unsupported cache type: ghost"));
        assertTrue(exception.getMessage().contains("jedis"));
        assertTrue(exception.getMessage().contains("caffeine"));
    }

    /**
     * {@code types()} 不可修改。
     */
    @Test
    public void shouldExposeUnmodifiableTypes() {
        CacheProviderRegistry registry = new CacheProviderRegistry();
        assertThrows(UnsupportedOperationException.class, () -> registry.types().add("gamma"));
    }

    /**
     * 最小 provider 桩：<b>无状态</b>，不产出实例。
     */
    static final class StubProvider implements CacheProvider {

        private final String type;

        StubProvider(String type) {
            this.type = type;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public Cache buildCache(String instanceName, String prefix, Prop prop) {
            return null;
        }
    }
}
