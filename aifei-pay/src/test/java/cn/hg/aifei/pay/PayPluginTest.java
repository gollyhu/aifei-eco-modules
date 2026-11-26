/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.log.LogFactory;
import cn.aifei.util.Prop;
import cn.hg.aifei.pay.aifei.AliPayPlugin;
import cn.hg.aifei.pay.internal.PayHolder;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestProps;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 装配生命周期（AC-01）：{@code start()} 后容器可注入、门面可取到同一实例；配置非法启动即失败且不留半装配；
 * 重复 {@code start()} / {@code stop()} 幂等。
 */
public class PayPluginTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 未装配 / 已停止的固定消息。
     */
    private static final String NOT_INSTALLED = "支付宝客户端未装配或已停止";

    /**
     * 被替换前的 AOP 工厂，用例结束后恢复。
     */
    private AopFactory previousFactory;

    /**
     * 换入前的日志工厂。
     */
    private LogFactory previousLogFactory;

    /**
     * 当前用例的插件实例。
     */
    private AliPayPlugin plugin;

    /**
     * 替换 AOP 工厂以隔离单例，并清空门面实例。
     */
    @Before
    public void replaceAopFactory() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        previousLogFactory = NoOpLogFactory.install();
        PayHolder.clear();
        plugin = null;
    }

    /**
     * 恢复环境：停止插件、清空门面并还原 AOP 与日志工厂。
     */
    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
        }
        PayHolder.clear();
        AopKit.get().setAopFactory(previousFactory);
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * 构造参数为 {@code null} 时失败。
     */
    @Test
    public void shouldRejectNullProp() {
        assertThrows(IllegalArgumentException.class, () -> new AliPayPlugin(null));
    }

    /**
     * AC-01：{@code start()} 后 {@code @Inject AliPayClient} 非 null，且与 {@code AliPayKit.client()} 是同一对象。
     */
    @Test
    public void shouldRegisterClientSingleton() {
        plugin = new AliPayPlugin(prop(PayTestProps.withCallbacks(PayTestProps.keyMode(APP))));
        plugin.start();

        AliPayClient injected = Aop.get(AliPayClient.class);

        assertNotNull("@Inject AliPayClient 不应为空", injected);
        assertSame("门面与容器应返回同一实例", injected, AliPayKit.client());
    }

    /**
     * AC-01 / AC-05：配置非法 → 启动失败且不留半装配（门面仍不可用）；修正配置后重新启动正常。
     */
    @Test
    public void shouldFailFastOnInvalidConfigAndLeaveNoHalfState() {
        Properties invalid = PayTestProps.without(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "env"));
        plugin = new AliPayPlugin(prop(invalid));

        AliPayException e = assertThrows(AliPayException.class, plugin::start);

        assertEquals("消息应含缺失键名", "支付宝配置缺失：" + PayTestProps.key(APP, "env"), e.getMessage());
        AliPayException notInstalled = assertThrows(AliPayException.class, AliPayKit::client);
        assertEquals("启动失败不得留下半装配态", NOT_INSTALLED, notInstalled.getMessage());

        plugin = new AliPayPlugin(prop(PayTestProps.withCallbacks(PayTestProps.keyMode(APP))));
        plugin.start();

        assertNotNull("修正配置后应能正常装配", AliPayKit.client());
    }

    /**
     * AC-01：重复 {@code start()} / {@code stop()} 幂等无异常；停止后门面调用失败。
     */
    @Test
    public void shouldBeIdempotent() {
        plugin = new AliPayPlugin(prop(PayTestProps.withCallbacks(PayTestProps.keyMode(APP))));
        plugin.start();
        plugin.start();

        assertNotNull(AliPayKit.client());

        plugin.stop();
        plugin.stop();

        AliPayException e = assertThrows(AliPayException.class, AliPayKit::client);
        assertEquals(NOT_INSTALLED, e.getMessage());
    }

    /**
     * AC-01：停止后再次启动复用首次装配的客户端，不重复向容器注册（否则容器会报单例冲突）。
     */
    @Test
    public void shouldReuseClientAcrossRestart() {
        plugin = new AliPayPlugin(prop(PayTestProps.withCallbacks(PayTestProps.keyMode(APP))));
        plugin.start();
        AliPayClient first = AliPayKit.client();
        plugin.stop();

        plugin.start();

        assertSame("再次启动应复用首次装配的实例", first, AliPayKit.client());
        assertSame("容器内仍应是同一实例", first, Aop.get(AliPayClient.class));
    }

    /**
     * AC-01：公钥证书模式同样可完成装配（证书以**内容**形态注入，无需落盘——设计 §14.2 V-5 的落点之一）。
     */
    @Test
    public void shouldStartWithCertificateMode() {
        plugin = new AliPayPlugin(prop(PayTestProps.certMode(APP)));

        plugin.start();

        assertNotNull("证书模式装配后应可注入客户端", Aop.get(AliPayClient.class));
        assertSame(Aop.get(AliPayClient.class), AliPayKit.client());
    }

    /**
     * 构造配置源。
     *
     * @param properties 配置项
     * @return 配置源
     */
    private static Prop prop(Properties properties) {
        Prop prop = new Prop();
        prop.getProperties().putAll(properties);
        return prop;
    }
}
