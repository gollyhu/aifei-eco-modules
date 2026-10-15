/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.mq.aifei.MqPlugin;
import cn.hg.aifei.mq.internal.MqHolder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link MqPlugin} 的装配生命周期、幂等与「未注册后端启动失败不留半装配」。
 */
public class MqPluginTest {

    /**
     * 被替换前的 AOP 工厂，用例结束后恢复。
     */
    private AopFactory previousFactory;

    /**
     * 当前用例的插件实例。
     */
    private MqPlugin plugin;

    /**
     * 替换 AOP 工厂隔离单例，并清空单例持有。
     */
    @Before
    public void replaceAopFactory() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        MqHolder.clear();
        plugin = null;
    }

    /**
     * 恢复环境：关闭已启动插件并还原 AOP 工厂。
     */
    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
        }
        MqHolder.clear();
        AopKit.get().setAopFactory(previousFactory);
    }

    /**
     * 构造参数为 {@code null} 时失败。
     */
    @Test
    public void shouldRejectNullProp() {
        assertThrows(IllegalArgumentException.class, () -> new MqPlugin(null));
    }

    /**
     * 启动后单例可用，发送与订阅闭环可用。
     */
    @Test
    public void shouldStartAndRegisterInstances() {
        plugin = new MqPlugin(prop("mq.type", "memory"));
        plugin.start();

        List<String> received = new ArrayList<>();
        MqKit.subscribe("orders", message ->
                received.add(new String(message.payload(), StandardCharsets.UTF_8)));
        SendResult result = MqKit.send("orders", "A1001");

        assertEquals("orders", result.topic());
        assertEquals(1, received.size());
        assertEquals("A1001", received.get(0));
        assertSame(MqKit.producer(), MqKit.producer());
        assertSame(MqKit.consumer(), MqKit.consumer());
    }

    /**
     * 未配置 {@code mq.type} 时使用默认内存后端启动。
     */
    @Test
    public void shouldStartWithDefaultType() {
        plugin = new MqPlugin(prop());
        plugin.start();

        SendResult result = MqKit.send("t", "x");

        assertEquals("t", result.topic());
    }

    /**
     * 重复启动不重复装配，取到的单例不变。
     */
    @Test
    public void shouldBeIdempotentOnStart() {
        plugin = new MqPlugin(prop());
        plugin.start();
        Producer first = MqKit.producer();

        plugin.start();

        assertSame(first, MqKit.producer());
    }

    /**
     * 停止后取单例失败，重复停止不抛异常。
     */
    @Test
    public void shouldBeIdempotentOnStop() {
        plugin = new MqPlugin(prop());
        plugin.start();

        plugin.stop();

        assertThrows(IllegalStateException.class, MqKit::producer);
        plugin.stop();
    }

    /**
     * 未启动就停止是空操作。
     */
    @Test
    public void shouldIgnoreStopBeforeStart() {
        plugin = new MqPlugin(prop());

        plugin.stop();

        assertThrows(IllegalStateException.class, MqKit::producer);
    }

    /**
     * 未注册的后端启动失败，且不留半装配状态。
     */
    @Test
    public void shouldFailFastOnUnregisteredType() {
        plugin = new MqPlugin(prop("mq.type", "kafka"));

        assertThrows(IllegalArgumentException.class, plugin::start);

        assertThrows(IllegalStateException.class, MqKit::producer);
        assertThrows(IllegalStateException.class, MqKit::consumer);
    }

    /**
     * 构造配置对象；交替出现的键与值。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置
     */
    private static Prop prop(String... keyValues) {
        Prop prop = new Prop();
        for (int i = 0; i < keyValues.length; i += 2) {
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }
}
