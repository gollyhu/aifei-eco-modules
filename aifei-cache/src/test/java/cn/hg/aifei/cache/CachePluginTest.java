/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.caffeine.CaffeineCache;
import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.cache.internal.Config;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static cn.hg.aifei.cache.TestSupport.prop;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link CachePlugin} 的配置解析、生命周期与注册表回滚用例。
 *
 * <p>构造函数只保留配置引用、不解析不校验，配置错误一律在 {@code start()} 阶段抛出；
 * 因此配置校验类断言统一用 {@link #rejectOnStart}。</p>
 */
public class CachePluginTest {

    private AopFactory previousFactory;

    private CachePlugin plugin;

    /**
     * 断言该配置在 {@code start()} 阶段被拒绝（构造期不解析配置）。
     *
     * @param factory 构造插件的工厂（保持与 {@code new CachePlugin(prop(...))} 相同的写法）
     * @return 捕获到的异常
     */
    private static IllegalArgumentException rejectOnStart(Supplier<CachePlugin> factory) {
        CachePlugin rejected = factory.get();
        return assertThrows(IllegalArgumentException.class, rejected::start);
    }

    /**
     * 替换 AOP 工厂，隔离 {@code Aop.get} 单例。
     */
    @Before
    public void replaceAopFactory() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        CacheManager.clear();
        plugin = null;
    }

    /**
     * 恢复环境，关闭已启动插件。
     */
    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
        }
        CacheManager.clear();
        AopKit.get().setAopFactory(previousFactory);
    }

    private static Prop caffeineConfig(String namespace) {
        return prop(
                "cache.type", "caffeine",
                "cache.ttlMillis", "60000",
                "cache.namespace", namespace
        );
    }

    /**
     * 最小 caffeine 配置可启动，并注册主 Cache / Counter 单例。
     */
    @Test
    public void shouldStartAndRegisterMainInstances() {
        plugin = new CachePlugin(caffeineConfig("main-ns"));
        plugin.start();

        Cache main = CacheManager.getCache(Config.MAIN_INSTANCE_NAME);
        assertSame(main, Aop.get(Cache.class));
        assertSame(CacheManager.getCounter(Config.MAIN_INSTANCE_NAME), Aop.get(Counter.class));
        assertSame(main, CacheUtil.use());

        CacheUtil.put("user", "1", "tom", Duration.ofSeconds(10));
        assertEquals("tom", Aop.<Cache>get(Cache.class).get("user", "1"));
    }

    /**
     * 配置校验在 {@code start()} 阶段完成；构造函数<b>不</b>解析配置。
     *
     * <p>构造期唯一的守卫是 {@code prop} 不能为 {@code null}，其余配置错误都要等到 {@code start()}
     * 才暴露 —— 这是「配置解析全部在 {@code start()}」决策的直接体现。</p>
     */
    @Test
    public void shouldValidateConfigOnStart() {
        assertThrows(IllegalArgumentException.class, () -> new CachePlugin(null));

        // 构造期不校验：这些实例本身可以建出来，错误在 start() 抛出
        new CachePlugin(prop());
        new CachePlugin(prop("cache.type", "Redis"));
        new CachePlugin(prop("cache.type", ""));

        rejectOnStart(() -> new CachePlugin(prop()));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "Redis")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "")));
    }

    /**
     * {@code start()} / {@code stop()} 幂等。
     */
    @Test
    public void shouldBeIdempotentOnStartAndStop() {
        plugin = new CachePlugin(caffeineConfig("ns"));
        plugin.start();
        Cache first = CacheUtil.use();
        plugin.start();
        assertSame(first, CacheUtil.use());

        plugin.stop();
        plugin.stop();

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, CacheUtil::use);
        assertTrue(exception.getMessage().contains("has not been initialized"));
    }

    /**
     * 扩展实例按名注册，且与主缓存命名空间隔离。
     */
    @Test
    public void shouldRegisterExtensionInstancesByName() {
        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.namespace", "main-ns",
                "cache.names", "mem",
                "cache.mem.type", "caffeine",
                "cache.mem.namespace", "mem-ns",
                "cache.mem.ttlMillis", "1000"
        ));
        plugin.start();

        Cache mem = CacheUtil.use("mem");
        assertEquals("mem-ns", mem.namespace());
        assertEquals(Duration.ofMillis(1_000L), mem.ttl());

        mem.put("user", "1", "mem-value", Duration.ofSeconds(10));
        CacheUtil.put("user", "1", "main-value", Duration.ofSeconds(10));
        assertEquals("mem-value", mem.get("user", "1"));
        assertEquals("main-value", CacheUtil.use().get("user", "1"));

        assertSame(mem, CacheManager.getCache("mem"));
        assertSame(CacheManager.getCounter("mem"), CounterUtil.use("mem"));
    }

    /**
     * 扩展实例缺省继承主缓存 ttl / cacheNull，但 namespace 不继承。
     */
    @Test
    public void shouldInheritExtensionDefaultsExceptNamespace() {
        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.ttlMillis", "1234",
                "cache.cacheNull", "true",
                "cache.namespace", "main-ns",
                "cache.names", "mem",
                "cache.mem.type", "caffeine"
        ));
        plugin.start();

        Cache mem = CacheUtil.use("mem");
        assertEquals(Duration.ofMillis(1_234L), mem.ttl());
        assertTrue(mem.cacheNull());
        assertEquals(Config.DEFAULT_NAMESPACE, mem.namespace());
    }

    /**
     * {@code cache.names} 空项必须抛异常。
     */
    @Test
    public void shouldRejectBlankInstanceNameInNames() {
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", "mem,,other")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", "mem,")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", ",mem")));
    }

    /**
     * {@code cache.names} 含 {@code __MAIN__} 必须抛异常。
     */
    @Test
    public void shouldRejectMainInstanceNameInNames() {
        IllegalArgumentException exception = rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", "__MAIN__")));
        assertTrue(exception.getMessage().startsWith("cache config invalid: cache.names="));
    }

    /**
     * 重名、保留名、含特殊字符的实例名必须抛异常。
     */
    @Test
    public void shouldRejectInvalidOrDuplicateInstanceNames() {
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", "a,a")));
        for (String reserved : new String[]{"type", "ttlMillis", "cacheNull", "namespace", "names", "main", "allowedPackages"}) {
            CachePlugin rejected =
                    new CachePlugin(prop("cache.type", "caffeine", "cache.names", reserved));
            assertThrows("保留名应被拒绝: " + reserved, IllegalArgumentException.class, rejected::start);
        }
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", "mem.cache")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.names", "mem:x")));
    }

    /**
     * 命名空间含分段符 {@code :} 必须被启动期拒绝，主缓存与扩展实例同规则。
     *
     * <p>命名空间是物理 key 的第一段，含 {@code :} 时拼接不再单射，两个不同实例可落到同一物理 key，
     * 而启动期排他判定按「唯一名 + 命名空间」判重、不会报冲突。校验在配置解析期完成，因此错误消息走
     * 配置错误的统一格式；只禁 {@code :}，其余字符不受影响。</p>
     */
    @Test
    public void shouldRejectNamespaceContainingSeparator() {
        IllegalArgumentException mainNamespace = rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.namespace", "a:b"
        )));
        assertTrue(mainNamespace.getMessage().startsWith("cache config invalid: cache.namespace=a:b,"));
        assertTrue(mainNamespace.getMessage().endsWith("reason=namespace must not contain ':'"));

        IllegalArgumentException extensionNamespace = rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.names", "mem",
                "cache.mem.type", "caffeine",
                "cache.mem.namespace", "a:b"
        )));
        assertTrue(extensionNamespace.getMessage()
                .startsWith("cache config invalid: cache.mem.namespace=a:b,"));

        // 只禁 ':'：其余标点不受影响
        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.namespace", "a-b_c.d"
        ));
        plugin.start();
        assertEquals("a-b_c.d", CacheUtil.use().namespace());
    }

    /**
     * 默认 TTL 的键名是 {@code cache.ttlMillis}：旧的 {@code cache.ttl} 不再被读取。
     *
     * <p>反证用毒值：旧键取「非字面整数」{@code 60_000}。它若被读取，{@code parseConfigLong}
     * 会立刻抛配置错误；因此「启动成功且取到默认值」等价于「旧键没被读」。</p>
     */
    @Test
    public void shouldIgnoreLegacyTtlKey() {
        plugin = new CachePlugin(prop("cache.type", "caffeine", "cache.ttl", "60_000"));
        plugin.start();
        assertEquals(Duration.ofMillis(Config.DEFAULT_TTL), CacheUtil.ttl());
    }

    /**
     * 扩展实例的 TTL 键名同样带 {@code Millis}：旧的 {@code cache.{name}.ttl} 被忽略后缺省继承根级 {@code cache.ttlMillis}。
     */
    @Test
    public void shouldIgnoreLegacyExtensionTtlKey() {
        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.ttlMillis", "5000",
                "cache.names", "mem",
                "cache.mem.type", "caffeine",
                "cache.mem.namespace", "mem-ns",
                "cache.mem.ttl", "60_000"                     // 旧写法：应被忽略
        ));
        plugin.start();
        assertEquals(Duration.ofMillis(5_000L), CacheUtil.use("mem").ttl());
    }

    @Test
    public void shouldMirrorCounterMembersInCounterUtil() {
        plugin = new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "1500"));
        plugin.start();

        assertEquals(Duration.ofMillis(1_500L), CounterUtil.ttl());
        assertTrue(CounterUtil.counterNames().isEmpty());

        assertEquals(1L, CounterUtil.increase("visits", "u1", 1L));
        assertEquals(2L, CounterUtil.increaseAndRefreshTtl("visits", "u1", 1L));
        assertEquals(1L, CounterUtil.decrease("visits", "u1", 1L));
        assertEquals(0L, CounterUtil.decreaseAndRefreshTtl("visits", "u1", 1L));
        assertEquals(Collections.singleton("visits"), CounterUtil.counterNames());

        CounterUtil.clear("visits");
        assertNull(CounterUtil.get("visits", "u1"));
        assertTrue("clear 后该计数名称必须移除", CounterUtil.counterNames().isEmpty());
    }

    /**
     * TTL 的两条规则：数值范围（1 ~ {@code Integer.MAX_VALUE} 秒）与字面量写法（不接受下划线、算术表达式）。
     */
    @Test
    public void shouldValidateTtl() {
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "0")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "-1")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "60_000")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "60 * 1000")));
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "2147483647001")));

        // 边界可用：Integer.MAX_VALUE 秒（毫秒）
        plugin = new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "2147483647000"));
        plugin.start();

        // boolean 只接受 true / false
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.cacheNull", "yes")));
    }

    /**
     * 缺省 TTL 与接口常量一致。
     */
    @Test
    public void shouldUseDefaultTtlEqualInterfaceConstant() {
        plugin = new CachePlugin(prop("cache.type", "caffeine"));
        plugin.start();
        assertEquals(Duration.ofMillis(Config.DEFAULT_TTL), CacheUtil.ttl());
        assertEquals(Config.DEFAULT_NAMESPACE, CacheUtil.use().namespace());
        assertFalse(CacheUtil.cacheNull());
    }

    /**
     * 空值配置视为未配置，示例配置必须能正常启动。
     */
    @Test
    public void shouldTreatBlankValuesAsUnconfigured() {
        plugin = new CachePlugin(prop(
                "cache.names", "mem",
                "cache.type", "jedis",
                "cache.cacheNull", "false",
                "cache.ttlMillis", "3600000",
                "cache.namespace", "aifei",
                "cache.jedis.host", "127.0.0.1",
                "cache.jedis.port", "6379",
                "cache.jedis.user", "",
                "cache.jedis.password", "",
                "cache.jedis.database", "0",
                "cache.jedis.ssl", "false",
                "cache.jedis.timeoutMillis", "2000",
                "cache.mem.type", "caffeine",
                "cache.mem.cacheNull", "false",
                "cache.mem.ttlMillis", "600000",
                "cache.mem.namespace", "mem",
                "cache.mem.caffeine.maxSize", "50000"
        ));

        plugin.start();

        assertEquals("aifei", CacheUtil.use().namespace());
        assertEquals(Duration.ofMillis(3_600_000L), CacheUtil.ttl());
        assertEquals("mem", CacheUtil.use("mem").namespace());
    }

    /**
     * 只含空白的取值经 {@code Prop.get} 归一为空串，可选键回落到缺省值而不是报错。
     */
    @Test
    public void shouldTreatWhitespaceOnlyValuesAsUnconfigured() {
        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.ttlMillis", "   ",
                "cache.namespace", "  ",
                "cache.cacheNull", " ",
                "cache.caffeine.maxSize", "  "
        ));
        plugin.start();

        assertEquals(Config.DEFAULT_TTL, CacheUtil.ttl().toMillis());
        assertEquals(Config.DEFAULT_NAMESPACE, CacheUtil.use().namespace());
        assertFalse(CacheUtil.cacheNull());

        // 必填键即使只含空白也必须失败
        rejectOnStart(() -> new CachePlugin(prop("cache.type", "   ")));
    }

    /**
     * {@code cache.jedis.uri} 与 host / port 同时配置不报错；URI 优先由 Redis 集成用例验证。
     */
    @Test
    public void shouldAcceptBothUriAndHostPort() {
        plugin = new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.jedis.uri", "redis://127.0.0.1:6379/0",
                "cache.jedis.host", "127.0.0.1",
                "cache.jedis.port", "6379"
        ));
        plugin.start();
        assertEquals(Config.DEFAULT_NAMESPACE, CacheUtil.use().namespace());
    }

    /**
     * 连接池上下限关系在解析期校验，且报错消息带 {@code jedis} 分段的<b>全键</b>。
     */
    @Test
    public void shouldValidatePoolBounds() {
        IllegalArgumentException maxIdleOverTotal = rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.maxTotal", "4", "cache.jedis.maxIdle", "8")));
        assertTrue(maxIdleOverTotal.getMessage(),
                maxIdleOverTotal.getMessage().startsWith("cache config invalid: cache.jedis.maxIdle=8"));
        assertTrue(maxIdleOverTotal.getMessage(),
                maxIdleOverTotal.getMessage().contains("cache.jedis.maxTotal"));

        rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.maxIdle", "4", "cache.jedis.minIdle", "8")));
        rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.maxTotal", "0")));
        rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.maxWaitMillis", "-2")));

        IllegalArgumentException badPort = rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.port", "70000")));
        assertTrue(badPort.getMessage(),
                badPort.getMessage().startsWith("cache config invalid: cache.jedis.port=70000"));

        // 合法组合：maxTotal = -1 表示不限制，不受 maxIdle 约束
        plugin = new CachePlugin(prop("cache.type", "jedis",
                "cache.jedis.maxTotal", "-1", "cache.jedis.maxIdle", "8", "cache.jedis.minIdle", "2"));
        plugin.start();
    }

    /**
     * jedis 键只从 {@code jedis} 分段读取，旧的平铺写法一律不再生效。
     *
     * <p>三段反证，每段都埋一个「若被读取就会留下痕迹」的旧键：① 破坏排他判定；② 触发配置错误；
     * ③ 非法取值使启动失败。三者都不发生，才说明旧键确实被忽略。</p>
     */
    @Test
    public void shouldReadJedisKeysOnlyFromJedisSegment() {
        // ① 旧键 cache.port=1 若生效，两个实例的唯一名会不同 → 排他校验不会触发
        plugin = new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.port", "1",                        // 旧写法：应被忽略
                "cache.ssl", "not-a-boolean",             // 旧写法：若被读取则报配置错误
                "cache.jedis.host", "10.0.0.7",
                "cache.jedis.port", "6380",
                "cache.names", "mem",
                "cache.mem.type", "jedis",
                "cache.mem.jedis.host", "10.0.0.7",
                "cache.mem.jedis.port", "6380"
        ));
        IllegalArgumentException duplicate =
                assertThrows(IllegalArgumentException.class, plugin::start);
        assertTrue(duplicate.getMessage(), duplicate.getMessage().contains("duplicate key space"));
        assertTrue(duplicate.getMessage(), duplicate.getMessage().contains("10.0.0.7:6380/0"));

        // ② 旧键 cache.maxTotal=0 属非法取值，被忽略后不影响启动；
        //    namespace 是不带后端分段的通用键，仍在根级平铺
        plugin = new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.maxTotal", "0",                    // 旧写法：应被忽略
                "cache.namespace", "only-segment"
        ));
        plugin.start();
        assertEquals("only-segment", CacheUtil.use().namespace());
    }

    /**
     * {@code cache.jedis.allowedPackages} 的解析：逗号分隔、空项即启动失败；空值与未配置等价，逐项去空白。
     */
    @Test
    public void shouldValidateAllowedPackages() {
        IllegalArgumentException blankItem = rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.allowedPackages", "com.foo,,com.bar")));
        assertTrue(blankItem.getMessage().startsWith("cache config invalid: cache.jedis.allowedPackages="));
        rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis", "cache.jedis.allowedPackages", "com.foo,")));

        // 空值等价于未配置，不产生任何放行项
        assertTrue(Config.resolveAllowedPackages(
                prop("cache.jedis.allowedPackages", "")).isEmpty());
        assertEquals(Arrays.asList("com.foo", "com.bar.*"), Config.resolveAllowedPackages(
                prop("cache.jedis.allowedPackages", " com.foo , com.bar.* ")));

        plugin = new CachePlugin(prop("cache.type", "jedis", "cache.jedis.allowedPackages", "com.foo"));
        plugin.start();
    }

    /**
     * 配置错误消息不得回显凭据：{@code cache.jedis.uri} 的 userInfo 必须脱敏。
     */
    @Test
    public void shouldNotLeakCredentialsInConfigErrors() {
        String secret = "sup3rSecret";
        IllegalArgumentException exception = rejectOnStart(() -> new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.jedis.uri", "redis://appuser:" + secret + "@127.0.0.1:6379/0 x")));

        assertFalse("配置错误消息不得包含口令明文：" + exception.getMessage(),
                exception.getMessage().contains(secret));
        assertTrue("协议与主机部分应保留以便定位：" + exception.getMessage(),
                exception.getMessage().contains("redis://***@127.0.0.1:6379/0"));
    }

    /**
     * 半装配失败后注册表必须回滚为空，不残留半成品实例。
     */
    @Test
    public void shouldRollbackRegistryOnHalfAssemblyFailure() {
        // 预先占用扩展实例名，使 start() 在注册第二个实例时失败
        CacheManager.register("mem", new CaffeineCache(100L));

        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.names", "mem",
                "cache.mem.type", "caffeine",
                "cache.mem.namespace", "mem-ns"
        ));

        assertThrows(IllegalStateException.class, plugin::start);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, CacheUtil::use);
        assertTrue(exception.getMessage().contains("has not been initialized"));
        assertThrows(IllegalStateException.class, () -> CacheUtil.use("mem"));
    }

    /**
     * 未在 {@code cache.names} 中声明的 {@code cache.{name}.*} 配置被忽略。
     */
    @Test
    public void shouldIgnoreUndeclaredExtensionConfig() {
        plugin = new CachePlugin(prop(
                "cache.type", "caffeine",
                "cache.namespace", "main-ns",
                "cache.ghost.type", "caffeine",
                "cache.ghost.namespace", "ghost-ns"
        ));
        plugin.start();

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> CacheUtil.use("ghost"));
        assertTrue(exception.getMessage().contains("unknown cache instance: ghost"));
    }

    /**
     * 配置校验消息格式遵循统一前缀。
     */
    @Test
    public void shouldUseUnifiedConfigErrorMessage() {
        IllegalArgumentException exception = rejectOnStart(() -> new CachePlugin(prop("cache.type", "caffeine", "cache.ttlMillis", "abc")));
        assertTrue(exception.getMessage(),
                exception.getMessage().startsWith("cache config invalid: cache.ttlMillis=abc, reason="));
    }

    /**
     * 实例名合法性校验器（非承诺 API）的直接用例。
     */
    @Test
    public void shouldValidateInstanceName() {
        assertTrue(Config.isValidInstanceName("mem"));
        assertFalse(Config.isValidInstanceName(null));
        assertFalse(Config.isValidInstanceName(" "));
        assertFalse(Config.isValidInstanceName("a.b"));
        assertFalse(Config.isValidInstanceName("a b"));
        assertFalse(Config.isValidInstanceName(Config.MAIN_INSTANCE_NAME));
    }

    // ─────────── 后端扩展点：类型白名单由注册表派生───────────

    /**
     * 端到端：两个 jedis 实例指向<b>同一</b> Redis 逻辑库、且都未显式配 {@code namespace}
     * （缺省都是 {@code aifei}）→ 启动期拒绝，不再静默互相覆盖。
     *
     * <p>这是「唯一名 + {@code namespace} 排他取代静默覆盖」的验收用例：构造期不失败
     * （那时只做「空值即未配置」的取值校验），失败点在 {@code start()} 的注册阶段。</p>
     */
    @Test
    public void shouldRejectDuplicateKeySpaceAcrossInstances() {
        plugin = new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.jedis.host", "127.0.0.1",
                "cache.jedis.port", "6379",
                "cache.names", "mem",
                "cache.mem.type", "jedis",
                "cache.mem.jedis.host", "127.0.0.1",
                "cache.mem.jedis.port", "6379"
        ));

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, plugin::start);
        assertTrue(exception.getMessage().contains("duplicate key space"));
        assertTrue(exception.getMessage().contains("127.0.0.1:6379/0"));

        IllegalStateException notInitialized =
                assertThrows(IllegalStateException.class, CacheUtil::use);
        assertTrue(notInitialized.getMessage().contains("has not been initialized"));
    }

    /**
     * 端到端：同一 Redis 逻辑库但 {@code namespace} 不同 → 放行（这是多实例的正规用法）。
     */
    @Test
    public void shouldAllowDistinctNamespaceOnSameRedis() {
        plugin = new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.jedis.host", "127.0.0.1",
                "cache.jedis.port", "6379",
                "cache.namespace", "main-ns",
                "cache.names", "mem",
                "cache.mem.type", "jedis",
                "cache.mem.jedis.host", "127.0.0.1",
                "cache.mem.jedis.port", "6379",
                "cache.mem.namespace", "mem-ns"
        ));
        plugin.start();

        assertEquals("main-ns", CacheUtil.use().namespace());
        assertEquals("mem-ns", CacheUtil.use("mem").namespace());
    }

    /**
     * 端到端：<b>不同</b> Redis 服务器、都未配 {@code namespace} → 放行（不能因为「都叫 aifei」就误判冲突）。
     */
    @Test
    public void shouldAllowSameNamespaceOnDistinctRedisHosts() {
        plugin = new CachePlugin(prop(
                "cache.type", "jedis",
                "cache.jedis.host", "10.0.0.1",
                "cache.jedis.port", "6379",
                "cache.names", "mem",
                "cache.mem.type", "jedis",
                "cache.mem.jedis.host", "10.0.0.2",
                "cache.mem.jedis.port", "6379"
        ));
        plugin.start();

        assertEquals(Config.DEFAULT_NAMESPACE, CacheUtil.use().namespace());
        assertEquals(Config.DEFAULT_NAMESPACE, CacheUtil.use("mem").namespace());
    }

    /**
     * 未注册的类型必须被拒，且消息里列出<b>已注册类型</b>。
     *
     * <p>同时钉住一个易错点：本后端的 {@code cache.type} 取值是 {@code jedis}（不是 {@code redis}）。</p>
     */
    @Test
    public void shouldRejectUnregisteredCacheType() {
        IllegalArgumentException exception = rejectOnStart(() -> new CachePlugin(prop("cache.type", "redis")));

        assertTrue(exception.getMessage().startsWith("cache config invalid: cache.type=redis"));
        assertTrue("消息应列出已注册类型 jedis / caffeine",
                exception.getMessage().contains("jedis") && exception.getMessage().contains("caffeine"));
    }

    /**
     * {@code start()} 中途失败时注册表必须清空，不残留半装配结果。
     *
     * <p>失败点选在框架桥：{@code AopFactory.addSingletonObject} 对已注册类型抛
     * {@code "Singleton object already exists"}，这条路在项目里确定可达。用例先注册一个
     * {@code Cache} 单例把桥堵住，再启动插件，此时实例已注册成功而桥失败，注册表必须被清空。</p>
     */
    @Test
    public void shouldClearRegistryWhenFrameworkBridgeFails() {
        AopKit.get().addSingletonObject(Cache.class, new CaffeineCache(100L));

        plugin = new CachePlugin(prop("cache.type", "caffeine"));

        assertThrows(RuntimeException.class, plugin::start);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, CacheUtil::use);
        assertTrue(exception.getMessage().contains("has not been initialized"));
    }
}
