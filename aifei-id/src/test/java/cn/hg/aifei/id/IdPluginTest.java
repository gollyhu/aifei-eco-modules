/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.log.LogKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.cache.CounterKit;
import cn.hg.aifei.cache.internal.CacheManager;
import cn.hg.aifei.cache.internal.Config;
import cn.hg.aifei.id.aifei.IdPlugin;
import cn.hg.aifei.id.internal.IdHolder;
import cn.hg.aifei.id.internal.IdNames;
import cn.hg.aifei.id.internal.SnowflakeIdGenerator;
import cn.hg.aifei.id.support.FakeCache;
import cn.hg.aifei.id.support.FakeCounter;
import cn.hg.aifei.id.support.FakeIdGenerator;
import cn.hg.aifei.id.support.FakeLockStore;
import cn.hg.aifei.id.support.FakeWorkerIdSource;
import cn.hg.aifei.id.support.HidingClassLoader;
import cn.hg.aifei.id.support.NoopLogFactory;
import cn.hg.aifei.lock.LockKit;
import cn.hg.aifei.lock.internal.LockHolder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 装配入口单元测试：生命周期（幂等、失败回滚、停机收敛）、workerId 三来源的装配分支，以及可选依赖缺失 /
 * 协同模块未装配两类启动期失败。
 *
 * <p>覆盖装配入口、发号、配置与停机等场景。全部用例
 * 只经由替身与 {@code Prop} 构造：{@code aifei-cache} 的实例用 {@link FakeCache} 注册进 {@code CacheManager}，
 * {@code aifei-lock} 的存储用 {@link FakeLockStore} 注册进 {@code LockHolder}，因此无需真实 Redis、
 * 无需真实锁，也不含任何秒级等待。</p>
 *
 * <p>消息断言全部按固定文案逐字比对（不使用 {@code IdErrors} 的构造方法拼期望值，否则措辞
 * 漂移无法被发现）；消息 P 的 {@code detail} 来自底层框架，只能动态取用，其前的模块措辞仍是字面量。</p>
 */
public class IdPluginTest {

    /**
     * 消息 A：{@code new IdPlugin(null)}。
     */
    private static final String MESSAGE_A = "prop can not be null";

    /**
     * 消息 E：租约丢失（本类固定用 workerId = 2 的来源）。
     */
    private static final String MESSAGE_E = "worker id lease lost, workerId: 2, "
            + "stop generating ids to avoid duplicates";

    /**
     * 消息 J：{@code aifei-cache} 缺失。
     */
    private static final String MESSAGE_J = "aifei-id requires module aifei-cache for this configuration, "
            + "add io.github.gollyhu:aifei-cache to your dependencies";

    /**
     * 消息 K：{@code aifei-lock} 缺失。
     */
    private static final String MESSAGE_K = "aifei-id requires module aifei-lock when id.worker.source=lock, "
            + "add io.github.gollyhu:aifei-lock to your dependencies";

    /**
     * 消息 L：{@code 0..1023} 全部被占用。
     */
    private static final String MESSAGE_L = "no worker id available in [0, 1023], "
            + "all leases are held by other instances";

    /**
     * 消息 H：缓存后端不是共享存储（{@code supportsCas=false}）。
     */
    private static final String MESSAGE_H = "aifei-cache backend is not a shared store (supportsCas=false), "
            + "aifei-id requires a shared backend such as redis";

    /**
     * 消息 N：{@code id.workerId} 超出上界。
     */
    private static final String MESSAGE_N_ABOVE = "id.workerId must be in [0, 1023], but was: 1024";

    /**
     * 消息 N：{@code id.workerId} 低于下界。
     */
    private static final String MESSAGE_N_BELOW = "id.workerId must be in [0, 1023], but was: -1";

    /**
     * 消息 O：未装配（停机后同样命中）。
     */
    private static final String MESSAGE_O =
            "IdGenerator not installed, add IdPlugin to AppConfig.config(Plugins) before using IdKit";

    /**
     * 消息 P 的模块措辞（{@code aifei-cache} 在 classpath 上但未装配）。
     */
    private static final String PREFIX_P_CACHE =
            "aifei-id assembly failed, aifei-cache is not ready, start it before IdPlugin: ";

    /**
     * 消息 P 的模块措辞（{@code aifei-lock} 在 classpath 上但未装配）。
     */
    private static final String PREFIX_P_LOCK =
            "aifei-id assembly failed, aifei-lock is not ready, start it before IdPlugin: ";

    /**
     * 号段键名，用于验证号段分支与本类无关的键被忽略。
     */
    private static final String SEGMENT_KEY = "order";

    /**
     * 用例替换前的 AOP 工厂，结束后恢复，避免污染其它测试类。
     */
    private AopFactory previousFactory;

    /**
     * 本用例装配的插件，用于结束后收敛。
     */
    private IdPlugin plugin;

    /**
     * 预装空日志工厂：{@code aifei-lock} 的 {@code Lock} 在类初始化期取 {@code Log}，装配入口启动成功也要
     * 记录一条 INFO；预装后这些路径真实执行且零副作用，同时保持「零日志绑定」纪律。
     */
    @BeforeClass
    public static void installNoopLogFactory() {
        LogKit.get().setLogFactory(new NoopLogFactory());
    }

    /**
     * 每个用例前清空持有器、换新 AOP 容器、清空 cache / lock 的替身注册表。
     */
    @Before
    public void isolateEnvironment() {
        IdHolder.clear();
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        CacheManager.clear();
        LockHolder.clear();
        plugin = null;
    }

    /**
     * 收敛：停机（幂等）、清空持有器与两只替身注册表、还原 AOP 容器。
     */
    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
            plugin = null;
        }
        IdHolder.clear();
        CacheManager.clear();
        LockHolder.clear();
        AopKit.get().setAopFactory(previousFactory);
    }

    // ─────────── 入口校验与生命周期 ───────────

    /**
     * 场景 1 / 消息 A：构造期拒绝空配置，不解析、不取 workerId。
     */
    @Test
    public void nullPropRejected() {
        try {
            new IdPlugin(null);
            fail("new IdPlugin(null) 应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(MESSAGE_A, e.getMessage());
        }
    }

    /**
     * 场景 18：重复启动幂等——不替换生成器，也不重复消耗 workerId（计数来源只被取一次）。
     */
    @Test
    public void startIsIdempotentWithoutReacquiringWorkerId() {
        FakeCounter counter = new FakeCounter();
        CacheManager.register(Config.MAIN_INSTANCE_NAME, FakeCache.shared(counter));

        plugin = new IdPlugin(prop("id.worker.source", "cache", "id.state.ttlMillis", "60000"));
        plugin.start();

        IdGenerator installed = IdHolder.get();
        long first = IdKit.nextId();
        plugin.start();

        assertSame("重复启动不得替换生成器", installed, IdHolder.get());
        assertEquals("重复启动不得重新取号", 1L,
                counter.valueOf(IdNames.CACHE_NAME, IdNames.WORKER_COUNTER_KEY));
        assertTrue("同一实例必须继续严格递增", IdKit.nextId() > first);
    }

    /**
     * 场景 19 / 消息 O：停机后门面立即失效；重复停机与未启动停机都是幂等空操作。
     */
    @Test
    public void stopIsIdempotentAndClearsHolder() {
        plugin = new IdPlugin(prop("id.workerId", "5"));
        plugin.start();
        assertTrue(IdKit.nextId() > 0L);

        plugin.stop();
        assertNotInstalled();
        plugin.stop();
        assertNotInstalled();

        IdPlugin neverStarted = new IdPlugin(prop("id.workerId", "6"));
        neverStarted.stop();
        assertNotInstalled();
    }

    /**
     * 失败回滚：装配在「workerId 来源已创建、生成器已安装」之后中断（框架同类型单例已存在）时，
     * 必须清空持有器并关闭已创建的来源（释放租约），不留半装配。
     *
     * <p>用 {@code lock} 来源而非 {@code config}：只有租约来源有可观测的释放动作，能证明回滚确实执行了
     * {@code close()}，而不是只清了一个字段。</p>
     */
    @Test
    public void startFailureLeavesNoPartialAssembly() {
        AopKit.get().addSingletonObject(IdGenerator.class, new FakeIdGenerator(1L));
        FakeLockStore store = FakeLockStore.freeFrom(5);
        LockHolder.register(store);

        try {
            new IdPlugin(prop("id.worker.source", "lock")).start();
            fail("同类型单例已存在时装配必须失败");
        } catch (IdException e) {
            fail("框架注册冲突不应被包装成 IdException：" + e.getMessage());
        } catch (RuntimeException expected) {
            // 框架按类型注册单例，重复注册即失败；本用例只关心回滚结果
        }

        assertNotInstalled();
        assertEquals("已创建的 workerId 来源必须被关闭并释放租约",
                Arrays.asList(IdNames.workerLockName(5)), store.released());
    }

    // ─────────── workerId 三来源 ───────────

    /**
     * 场景 16 / 消息 N：{@code source=config} 时 {@code id.workerId} 必须落在 {@code 0..1023}，越界拒绝
     * 装配且不留半装配。
     */
    @Test
    public void configWorkerIdOutOfRangeRejected() {
        assertStartFailsWith(prop("id.workerId", "1024"), MESSAGE_N_ABOVE);
        assertNotInstalled();

        assertStartFailsWith(prop("id.workerId", "-1"), MESSAGE_N_BELOW);
        assertNotInstalled();
    }

    /**
     * {@code cache} 来源：{@code (计数 - 1) % 1024}，并如实把分区名、计数键、步长与 TTL 下传。
     *
     * <p>预置计数为 1025，取号后为 1026，取模得 1——若实现退化成截断或越界判断，本用例立即失败。</p>
     */
    @Test
    public void cacheWorkerIdIsCounterModulo1024() {
        long ttlMillis = 60000L;
        FakeCounter counter = new FakeCounter();
        counter.increaseAndRefreshTtl(IdNames.CACHE_NAME, IdNames.WORKER_COUNTER_KEY, 1025L,
                Duration.ofMillis(ttlMillis));
        CacheManager.register(Config.MAIN_INSTANCE_NAME, FakeCache.shared(counter));

        plugin = new IdPlugin(prop("id.worker.source", "cache",
                "id.state.ttlMillis", String.valueOf(ttlMillis)));
        plugin.start();

        long id = IdKit.nextId();
        assertEquals("计数 1026 必须取模为 1", 1L, (id >> 12) & 0x3FFL);
        assertEquals(IdNames.CACHE_NAME, counter.lastCounterName());
        assertEquals(IdNames.WORKER_COUNTER_KEY, counter.lastKey());
        assertEquals(1L, counter.lastStep());
        assertEquals(Duration.ofMillis(ttlMillis), counter.lastTtl());
    }

    /**
     * {@code lock} 来源：{@code 0..1023} 升序抢占，首个成功即持有；停机释放该租约。
     */
    @Test
    public void lockWorkerIdAcquiredInAscendingOrder() {
        FakeLockStore store = FakeLockStore.freeFrom(3);
        LockHolder.register(store);

        plugin = new IdPlugin(prop("id.worker.source", "lock"));
        plugin.start();

        long id = IdKit.nextId();
        assertEquals("首个可用 workerId 是 3，必须持用它", 3L, (id >> 12) & 0x3FFL);
        assertEquals(Arrays.asList(
                IdNames.workerLockName(0),
                IdNames.workerLockName(1),
                IdNames.workerLockName(2),
                IdNames.workerLockName(3)), store.attempts());

        plugin.stop();
        assertEquals("停机必须释放本次持有的租约",
                Arrays.asList(IdNames.workerLockName(3)), store.released());
        assertNotInstalled();
    }

    /**
     * 场景 15 / 消息 L：{@code 0..1023} 全被其它实例占用即启动失败（不等待），且不留下任何租约。
     */
    @Test
    public void lockWorkerIdExhaustedFailsWithMessageL() {
        FakeLockStore store = FakeLockStore.allOccupied();
        LockHolder.register(store);

        assertStartFailsWith(prop("id.worker.source", "lock"), MESSAGE_L);

        List<String> attempts = store.attempts();
        assertEquals("必须把 0..1023 全部试一遍", IdNames.WORKER_ID_MODULUS, attempts.size());
        for (int i = 0; i < attempts.size(); i++) {
            assertEquals("抢占必须按 workerId 升序进行", IdNames.workerLockName(i), attempts.get(i));
        }
        assertTrue("一个都没抢到，因此不应有释放", store.released().isEmpty());
        assertNotInstalled();
    }

    /**
     * 场景 5 / 消息 E：运行期租约丢失后必须拒绝发号。
     *
     * <p>真实的「续期失败置不可用」需要等一个续期周期（下限 1000ms），离线单测纪律不允许秒级等待，
     * 因此用 {@link FakeWorkerIdSource#breakLease()} 把「租约丢失」做成可控输入；
     * 装配后 {@code IdKit} 与注入引用都走同一个持有器，本用例经由持有器验证同一条失败路径。</p>
     */
    @Test
    public void leaseLossStopsGenerationWithMessageE() {
        FakeWorkerIdSource source = new FakeWorkerIdSource(2);
        IdHolder.install(new SnowflakeIdGenerator(source));
        assertTrue(IdKit.nextId() > 0L);

        source.breakLease();
        try {
            IdKit.nextId();
            fail("租约丢失后必须拒绝发号");
        } catch (IdException e) {
            assertEquals(MESSAGE_E, e.getMessage());
        }
        assertTrue("租约丢失必须把来源置为不可用", !source.healthy());
    }

    // ─────────── 可选依赖缺失与协同模块未装配 ───────────

    /**
     * 场景 12 / 13 / 消息 J：{@code aifei-cache} 不在 classpath 上时，{@code cache} 来源与
     * {@code segment}+{@code cache} 两条分支都在启动期给出带坐标的可读消息，且绝不出现
     * {@code NoClassDefFoundError}。
     */
    @Test
    public void missingCacheDependencyFailsWithMessageJ() {
        assertStartFailsWithHiddenClass(MESSAGE_J, "cn.hg.aifei.cache.CounterKit",
                "id.strategy", "snowflake", "id.worker.source", "cache");
        assertStartFailsWithHiddenClass(MESSAGE_J, "cn.hg.aifei.cache.CounterKit",
                "id.strategy", "segment", "id.segment.store", "cache");
    }

    /**
     * 场景 14 / 消息 K：{@code aifei-lock} 不在 classpath 上时，{@code source=lock} 在启动期给出带坐标的
     * 可读消息，且绝不出现 {@code NoClassDefFoundError}。
     */
    @Test
    public void missingLockDependencyFailsWithMessageK() {
        assertStartFailsWithHiddenClass(MESSAGE_K, "cn.hg.aifei.lock.LockKit",
                "id.worker.source", "lock");
    }

    /**
     * 场景 23 / 消息 P：{@code aifei-cache} 在 classpath 上但 {@code CachePlugin} 未装配，启动期拒绝且不留
     * 半装配。
     */
    @Test
    public void unassembledCacheDependencyFailsWithMessageP() {
        String detail = unassembledCacheDetail();
        assertStartFailsWith(prop("id.worker.source", "cache"), PREFIX_P_CACHE + detail);
        assertNotInstalled();
    }

    /**
     * 场景 23 / 消息 P：{@code aifei-lock} 在 classpath 上但 {@code LockPlugin} 未装配（或装配顺序不对），
     * 启动期拒绝且不留半装配。
     */
    @Test
    public void unassembledLockDependencyFailsWithMessageP() {
        String detail = unassembledLockDetail();
        assertStartFailsWith(prop("id.worker.source", "lock"), PREFIX_P_LOCK + detail);
        assertNotInstalled();
    }

    /**
     * 场景 10 / 消息 H：缓存后端不支持条件操作（本地 Caffeine 形态）时启动期拒绝装配且不留半装配。
     *
     * <p>{@code cache} 来源与 {@code segment}+{@code cache} 两条分支都要求共享后端：非共享后端下多实例会
     * 各自从 1 开始取号，必然重号，因此不允许降级。桩用 {@link FakeCache#notShared} 只改
     * {@code supportsCas()} 一项取值，其余装配代码全部真实执行。</p>
     */
    @Test
    public void notSharedBackendFailsWithMessageH() {
        CacheManager.register(Config.MAIN_INSTANCE_NAME, FakeCache.notShared(new FakeCounter()));

        assertStartFailsWith(prop("id.worker.source", "cache"), MESSAGE_H);
        assertNotInstalled();

        assertStartFailsWith(prop("id.strategy", "segment", "id.segment.store", "cache"), MESSAGE_H);
        assertNotInstalled();
    }

    /**
     * 场景 22 的装配侧证据：{@code id.strategy=snowflake} 下残留的号段键被忽略，不影响启动。
     */
    @Test
    public void segmentKeysIgnoredUnderSnowflake() {
        plugin = new IdPlugin(prop("id.workerId", "9", "id.segment.key", SEGMENT_KEY,
                "id.segment.step", "8"));
        plugin.start();
        long id = IdKit.nextId();
        assertTrue(id > 0L);
        assertEquals(9L, (id >> 12) & 0x3FFL);
    }

    // ─────────── 工具 ───────────

    /**
     * 断言装配以指定消息的 {@link IdException} 失败。
     *
     * @param prop 配置
     * @param expectedMessage 期望的逐字消息
     */
    private static void assertStartFailsWith(Prop prop, String expectedMessage) {
        try {
            new IdPlugin(prop).start();
            fail("装配必须以该消息失败：" + expectedMessage);
        } catch (IdException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    /**
     * 在「隐藏了指定类」的上下文类加载器下启动装配入口，断言以指定消息的 {@link IdException} 失败，且不出现
     * {@code NoClassDefFoundError}。
     *
     * @param expectedMessage 期望的逐字消息
     * @param hiddenClassName 伪装成不存在的全限定类名
     * @param keyValues 交替出现的键与值
     */
    private static void assertStartFailsWithHiddenClass(String expectedMessage, String hiddenClassName,
                                                        String... keyValues) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new HidingClassLoader(original, hiddenClassName));
        try {
            new IdPlugin(prop(keyValues)).start();
            fail("可选依赖缺失时装配必须失败：" + expectedMessage);
        } catch (NoClassDefFoundError e) {
            fail("可选依赖缺失必须由 IdDeps 探测成 IdException，不得出现 NoClassDefFoundError: " + e);
        } catch (IdException e) {
            assertEquals(expectedMessage, e.getMessage());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
        assertNotInstalled();
    }

    /**
     * 断言当前处于「未装配」状态：门面抛 {@link IllegalStateException}，消息 O 逐字。
     */
    private static void assertNotInstalled() {
        try {
            IdKit.nextId();
            fail("未装配时 IdKit.nextId() 应抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(MESSAGE_O, e.getMessage());
        }
    }

    /**
     * 取「{@code CachePlugin} 未启动」时框架抛出的说明文本，用于拼出消息 P 的完整期望值。
     *
     * @return 底层异常消息
     */
    private static String unassembledCacheDetail() {
        try {
            CounterKit.use();
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        fail("CachePlugin 未启动时 CounterKit.use() 必须抛 IllegalStateException");
        return null;
    }

    /**
     * 取「{@code LockPlugin} 未启动」时框架抛出的说明文本，用于拼出消息 P 的完整期望值。
     *
     * @return 底层异常消息
     */
    private static String unassembledLockDetail() {
        try {
            LockKit.tryLock(IdNames.workerLockName(IdNames.MIN_WORKER_ID), Duration.ofMillis(1000L));
        } catch (RuntimeException e) {
            return e.getMessage();
        }
        fail("LockPlugin 未启动时 LockKit.tryLock 必须抛异常");
        return null;
    }

    /**
     * 以键值对构造配置。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置对象
     */
    private static Prop prop(String... keyValues) {
        Prop prop = new Prop();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }
}
