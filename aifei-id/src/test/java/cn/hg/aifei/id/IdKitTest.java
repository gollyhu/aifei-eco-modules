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
import cn.hg.aifei.id.aifei.IdPlugin;
import cn.hg.aifei.id.internal.IdHolder;
import cn.hg.aifei.id.support.FakeIdGenerator;
import cn.hg.aifei.id.support.NoopLogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 取用门面单元测试：未装配显式失败、转发语义、十进制字符串精度，以及装配入口对门面的生命周期驱动。
 *
 * <p>覆盖装配入口入参校验、未装配 / 已停机调用、重复启动幂等、
 * 19（重复或未启动停机幂等）的门面部分。装配层更完整的用例（依赖缺失、租约释放等）
 * 由装配测试承担。</p>
 *
 * <p>「已停机」这一状态只能由真实装配入口产生，因此本类用 {@link IdPlugin} 装配一个 {@code config} 来源的
 * 雪花生成器——这也是模块默认、零外部依赖的路径。</p>
 */
public class IdKitTest {

    /**
     * 未装配时的异常消息。
     */
    private static final String NOT_INSTALLED_MESSAGE =
            "IdGenerator not installed, add IdPlugin to AppConfig.config(Plugins) before using IdKit";

    /**
     * 超出 JS 安全整数范围（2^53）的 19 位值，用于验证字符串形态不丢精度。
     */
    private static final long BIG_ID = 9007199254740993L;

    /**
     * 本类装配的插件，用于在用例结束后收敛。
     */
    private IdPlugin plugin;

    /**
     * 预装空日志工厂：装配入口在启动成功后记录一条 INFO，内核默认工厂取用即抛异常、会被装配层吞掉，
     * 预装后该记录路径真实执行一次且零副作用。
     */
    @BeforeClass
    public static void installNoopLogFactory() {
        LogKit.get().setLogFactory(new NoopLogFactory());
    }

    /**
     * 每个用例前清空持有器并换一个全新的 AOP 容器。
     *
     * <p>框架没有单例注销 API（装配层契约也据此声明「停机后容器中的引用仍在」），同一个 JVM 内第二个装配
     * 入口实例注册 {@link IdGenerator} 单例时会因「已存在」失败。用例之间换容器即可隔离，不改动被测代码。</p>
     */
    @Before
    public void resetState() {
        IdHolder.clear();
        AopKit.get().setAopFactory(new AopFactory());
    }

    /**
     * 收敛装配状态，避免用例之间互相影响。
     */
    @After
    public void stopPlugin() {
        if (plugin != null) {
            plugin.stop();
            plugin = null;
        }
        IdHolder.clear();
    }

    // ─────────── 场景 17：未装配 ───────────

    /**
     * 场景 17：未装配时两个门面方法都抛 {@link IllegalStateException}，消息 O 逐字。
     */
    @Test
    public void notInstalledFailsExplicitly() {
        try {
            IdKit.nextId();
            fail("未装配时 IdKit.nextId() 应抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(NOT_INSTALLED_MESSAGE, e.getMessage());
        }
        try {
            IdKit.nextIdStr();
            fail("未装配时 IdKit.nextIdStr() 应抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(NOT_INSTALLED_MESSAGE, e.getMessage());
        }
    }

    // ─────────── 转发语义 ───────────

    /**
     * 门面只是转发：返回值来自当前装配的生成器，字符串形态等价 {@code Long.toString}。
     */
    @Test
    public void facadeForwardsToInstalledGenerator() {
        IdHolder.install(new FakeIdGenerator(BIG_ID));
        assertEquals("9007199254740993", IdKit.nextIdStr());
        assertEquals(BIG_ID + 1L, IdKit.nextId());
    }

    // ─────────── 场景 18 / 19：装配入口生命周期 ───────────

    /**
     * 装配后门面可用，取到的 ID 落在配置的 workerId 位上。
     */
    @Test
    public void pluginStartMakesFacadeAvailable() {
        plugin = new IdPlugin(prop("id.workerId", "7"));
        plugin.start();
        long id = IdKit.nextId();
        assertTrue("ID 必须恒正", id > 0L);
        assertEquals(7L, (id >> 12) & 0x3FFL);
        assertTrue("字符串形态必须是可直接转回 long 的十进制表示",
                Long.parseLong(IdKit.nextIdStr()) > 0L);
    }

    /**
     * 场景 18：重复启动幂等——不重复装配，装配后的生成器实例保持不变。
     */
    @Test
    public void pluginStartIsIdempotent() {
        plugin = new IdPlugin(prop("id.workerId", "1"));
        plugin.start();
        IdGenerator installed = IdHolder.get();
        long first = IdKit.nextId();
        plugin.start();
        assertSame("重复启动不得替换生成器", installed, IdHolder.get());
        assertTrue("同一实例必须继续严格递增", IdKit.nextId() > first);
    }

    /**
     * 场景 19：停机后门面抛未装配异常；重复停机与未启动停机都是幂等空操作。
     */
    @Test
    public void pluginStopIsIdempotent() {
        plugin = new IdPlugin(prop("id.workerId", "2"));
        plugin.start();
        assertTrue(IdKit.nextId() > 0L);
        plugin.stop();
        try {
            IdKit.nextId();
            fail("停机后 IdKit.nextId() 应抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(NOT_INSTALLED_MESSAGE, e.getMessage());
        }
        plugin.stop();

        IdPlugin neverStarted = new IdPlugin(prop("id.workerId", "3"));
        neverStarted.stop();
        try {
            IdKit.nextId();
            fail("未启动的插件停机后 IdKit 仍应抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(NOT_INSTALLED_MESSAGE, e.getMessage());
        }
    }

    /**
     * 场景 1：装配入口拒绝空配置，消息 A 逐字。
     */
    @Test
    public void nullPropRejected() {
        try {
            new IdPlugin(null);
            fail("new IdPlugin(null) 应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("prop can not be null", e.getMessage());
        }
    }

    // ─────────── 工具 ───────────

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
