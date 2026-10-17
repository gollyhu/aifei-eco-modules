/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.util.Prop;
import cn.hg.aifei.mq.aifei.MqPlugin;
import cn.hg.aifei.mq.internal.MqHolder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link MqPlugin} 的装配生命周期、多实例装配、幂等与「装配失败不留半装配」。
 */
public class MqPluginTest {

    /**
     * 命名实例名。
     */
    private static final String ORDERS = "orders";

    /**
     * 被替换前的 AOP 工厂，用例结束后恢复。
     */
    private AopFactory previousFactory;

    /**
     * 当前用例的插件实例。
     */
    private MqPlugin plugin;

    /**
     * 替换 AOP 工厂隔离单例，并清空实例登记。
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
     * 启动后实例可用，发送与订阅闭环可用；{@code @Inject} 单例就是主实例。
     */
    @Test
    public void shouldStartAndRegisterInstances() {
        plugin = new MqPlugin(prop("mq.type", "memory"));
        plugin.start();

        List<String> received = new ArrayList<>();
        ConsumerUtil.subscribe("orders", message -> received.add(text(message)));
        SendResult result = ProducerUtil.send("orders", "A1001");

        assertEquals("orders", result.topic());
        assertEquals(1, received.size());
        assertEquals("A1001", received.get(0));
        assertSame(ProducerUtil.use(), ProducerUtil.use());
        assertSame(ConsumerUtil.use(), ConsumerUtil.use());
        assertSame(Aop.get(Producer.class), ProducerUtil.use());
        assertSame(Aop.get(Consumer.class), ConsumerUtil.use());
    }

    /**
     * 未配置 {@code mq.type} 时使用默认内存后端启动。
     */
    @Test
    public void shouldStartWithDefaultType() {
        plugin = new MqPlugin(prop());
        plugin.start();

        SendResult result = ProducerUtil.send("t", "x");

        assertEquals("t", result.topic());
    }

    /**
     * 重复启动不重复装配，主实例与命名实例取到的对象都不变。
     */
    @Test
    public void shouldBeIdempotentOnStart() {
        plugin = new MqPlugin(prop("mq.names", ORDERS));
        plugin.start();
        Producer main = ProducerUtil.use();
        Producer orders = ProducerUtil.use(ORDERS);

        plugin.start();

        assertSame(main, ProducerUtil.use());
        assertSame(orders, ProducerUtil.use(ORDERS));
    }

    /**
     * 停止后主实例与命名实例都取不到，重复停止不抛异常。
     */
    @Test
    public void shouldBeIdempotentOnStop() {
        plugin = new MqPlugin(prop("mq.names", ORDERS));
        plugin.start();

        plugin.stop();

        assertThrows(IllegalStateException.class, ProducerUtil::use);
        assertThrows(IllegalStateException.class, ConsumerUtil::use);
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use(ORDERS));
        assertThrows(IllegalStateException.class, () -> ConsumerUtil.use(ORDERS));
        plugin.stop();
    }

    /**
     * 未启动就停止是空操作。
     */
    @Test
    public void shouldIgnoreStopBeforeStart() {
        plugin = new MqPlugin(prop());

        plugin.stop();

        assertThrows(IllegalStateException.class, ProducerUtil::use);
    }

    /**
     * 未注册的后端启动失败，且不留半装配状态。
     */
    @Test
    public void shouldFailFastOnUnregisteredType() {
        plugin = new MqPlugin(prop("mq.type", "nosuch"));

        assertThrows(IllegalArgumentException.class, plugin::start);

        assertThrows(IllegalStateException.class, ProducerUtil::use);
        assertThrows(IllegalStateException.class, ConsumerUtil::use);
    }

    /**
     * {@code mq.names} 声明的命名实例各建一条总线：与主实例互相隔离，静态短写只作用于主实例。
     */
    @Test
    public void shouldAssembleNamedInstances() {
        plugin = new MqPlugin(prop("mq.names", ORDERS + ",payments"));
        plugin.start();

        Producer orders = ProducerUtil.use(ORDERS);
        assertNotSame(ProducerUtil.use(), orders);
        assertNotSame(orders, ProducerUtil.use("payments"));
        assertSame(orders, ProducerUtil.use(ORDERS));

        List<String> mainReceived = new ArrayList<>();
        List<String> ordersReceived = new ArrayList<>();
        ConsumerUtil.subscribe("t", message -> mainReceived.add(text(message)));
        ConsumerUtil.use(ORDERS).subscribe("t", message -> ordersReceived.add(text(message)));

        ProducerUtil.use(ORDERS).send("t", "for-orders");

        assertEquals(1, ordersReceived.size());
        assertEquals("for-orders", ordersReceived.get(0));
        assertTrue("命名实例的消息不得投递给主实例的订阅者", mainReceived.isEmpty());

        ProducerUtil.send("t", "for-main");

        assertEquals(1, mainReceived.size());
        assertEquals("for-main", mainReceived.get(0));
        assertEquals("主实例的消息不得投递给命名实例的订阅者", 1, ordersReceived.size());
    }

    /**
     * {@code mq.names} 非法时启动失败，且不留半装配状态。
     */
    @Test
    public void shouldRejectInvalidInstanceNames() {
        plugin = new MqPlugin(prop("mq.names", ORDERS + ",,payments"));

        assertThrows(IllegalArgumentException.class, plugin::start);

        assertThrows(IllegalStateException.class, ProducerUtil::use);
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use(ORDERS));
    }

    /**
     * 命名实例的类型未注册时启动失败，已装配的主实例一并回滚。
     */
    @Test
    public void shouldRollBackWhenNamedInstanceFails() {
        plugin = new MqPlugin(prop("mq.names", ORDERS, "mq.orders.type", "nosuch"));

        assertThrows(IllegalArgumentException.class, plugin::start);

        assertThrows(IllegalStateException.class, ProducerUtil::use);
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use(ORDERS));
    }

    /**
     * 装配中途失败回滚时，失败前已建成的主实例总线必须已被 {@code close()}。
     *
     * <p>场景：主实例先建成并登记，随后 {@code orders} 实例的类型未注册而中止。用例在
     * {@code orders} 的配置解析处（此刻主实例仍在登记中）抓住主实例的生产视图，装配失败后直接观测该视图
     * 的可观测行为——已建总线若已关闭，则再次 {@code send} 抛 {@link IllegalStateException}。若回滚中省去
     * {@code close()}，总线仍可用、{@code send} 正常返回，本用例即失败。</p>
     */
    @Test
    public void shouldCloseAssembledBusWhenAssemblyFailsMidway() {
        CapturingProp capturingProp = new CapturingProp("mq.orders.type");
        capturingProp.getProperties().setProperty("mq.names", ORDERS);
        capturingProp.getProperties().setProperty("mq.orders.type", "nosuch");
        plugin = new MqPlugin(capturingProp);

        assertThrows(IllegalArgumentException.class, plugin::start);

        Producer assembled = capturingProp.assembledProducer();
        assertNotNull("失败前主实例总线已建成并登记，应能捕获其生产视图", assembled);
        assertThrows(
                "回滚必须关闭已建总线：已关闭总线的生产视图再次 send 应抛 IllegalStateException",
                IllegalStateException.class,
                () -> assembled.send("t", "x")
        );
    }

    /**
     * 框架桥失败（同类型单例已存在）时，已装配的实例必须全部回滚，不残留半装配结果。
     */
    @Test
    public void shouldClearInstancesWhenFrameworkBridgeFails() {
        AopKit.get().addSingletonObject(Producer.class, new StubProducer());

        plugin = new MqPlugin(prop("mq.names", ORDERS));

        assertThrows(RuntimeException.class, plugin::start);

        assertThrows(IllegalStateException.class, ProducerUtil::use);
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use(ORDERS));
    }

    /**
     * 取消息载荷的 UTF-8 文本。
     *
     * @param message 消息
     * @return 文本载荷
     */
    private static String text(Message message) {
        return new String(message.payload(), StandardCharsets.UTF_8);
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

    /**
     * 仅用于占用 {@code @Inject} 单例槽位的生产者。
     */
    private static final class StubProducer implements Producer {

        @Override
        public SendResult send(Message message) {
            return null;
        }

        @Override
        public SendResult send(String topic, byte[] payload) {
            return null;
        }

        @Override
        public SendResult send(String topic, String text) {
            return null;
        }
    }

    /**
     * 在读取指定配置键的那一刻捕获当前主实例生产视图的配置源。
     *
     * <p>捕获发生在 {@code MqConfig.parse} 读取该键时，此刻主实例已登记在 {@code MqHolder} 中；只捕获一次。
     * 借此可在装配失败、登记被清除之后仍持有「失败前已建总线」的视图，用于直接观测其是否已关闭。</p>
     */
    private static final class CapturingProp extends Prop {

        /**
         * 承载配置项、并在读取触发键时完成捕获的容器。
         */
        private final CapturingProperties capturingProperties;

        /**
         * 创建配置源。
         *
         * @param triggerKey 读取该键时触发捕获
         */
        private CapturingProp(String triggerKey) {
            this.capturingProperties = new CapturingProperties(triggerKey);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Properties getProperties() {
            return capturingProperties;
        }

        /**
         * 返回捕获到的主实例生产视图；未触发捕获时为 {@code null}。
         *
         * @return 主实例生产视图
         */
        private Producer assembledProducer() {
            return capturingProperties.assembledProducer;
        }
    }

    /**
     * 配置项容器：读取触发键时从 {@code MqHolder} 抓取当前主实例生产视图，只抓取一次。
     */
    private static final class CapturingProperties extends Properties {

        private static final long serialVersionUID = 1L;

        /**
         * 触发捕获的配置键。
         */
        private final String triggerKey;

        /**
         * 已捕获的主实例生产视图。
         */
        private Producer assembledProducer;

        /**
         * 创建容器。
         *
         * @param triggerKey 读取该键时触发捕获
         */
        private CapturingProperties(String triggerKey) {
            this.triggerKey = triggerKey;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized String getProperty(String key) {
            if (assembledProducer == null && triggerKey.equals(key)) {
                assembledProducer = ProducerUtil.use();
            }
            return super.getProperty(key);
        }
    }
}
