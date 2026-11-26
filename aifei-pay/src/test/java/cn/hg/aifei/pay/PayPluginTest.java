/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.aop.Aop;
import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.log.LogFactory;
import cn.aifei.util.Prop;
import cn.hg.aifei.pay.aifei.PayPlugin;
import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestProps;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 装配生命周期：按通道注册、默认通道注入、非法配置启动失败与幂等停止。
 */
public class PayPluginTest {

    private static final String APP = PayTestProps.APP_MAIN;
    private static final String NOT_INSTALLED = "支付客户端未装配或已停止";

    private AopFactory previousFactory;
    private LogFactory previousLogFactory;
    private PayPlugin plugin;

    @Before
    public void replaceAopFactory() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        previousLogFactory = NoOpLogFactory.install();
        PayChannelRegistry.clear();
        plugin = null;
    }

    @After
    public void restoreEnvironment() {
        if (plugin != null) {
            plugin.stop();
        }
        PayChannelRegistry.clear();
        AopKit.get().setAopFactory(previousFactory);
        NoOpLogFactory.restore(previousLogFactory);
    }

    @Test
    public void shouldRejectNullProp() {
        assertThrows(IllegalArgumentException.class, () -> new PayPlugin(null));
    }

    @Test
    public void shouldRegisterDefaultClientSingleton() {
        plugin = new PayPlugin(prop(channelProps(PayTestProps.withCallbacks(PayTestProps.keyMode(APP)))));
        plugin.start();

        PayClient injected = Aop.get(PayClient.class);

        assertNotNull("@Inject PayClient 不应为空", injected);
        assertSame("门面与容器应返回同一实例", injected, PayKit.defaultClient());
        assertEquals("默认通道", "main", PayKit.defaultClient().channelName());
    }

    @Test
    public void shouldFailFastOnInvalidConfigAndLeaveNoHalfState() {
        Properties invalid = channelProps(PayTestProps.keyMode(APP));
        invalid.remove("pay.main.env");
        plugin = new PayPlugin(prop(invalid));

        PayException e = assertThrows(PayException.class, plugin::start);

        assertEquals("消息应含缺失键名", "支付配置缺失：pay.main.env", e.getMessage());
        PayException notInstalled = assertThrows(PayException.class, PayKit::defaultClient);
        assertEquals("启动失败不得留下半装配态", NOT_INSTALLED, notInstalled.getMessage());
    }

    @Test
    public void shouldBeIdempotent() {
        plugin = new PayPlugin(prop(channelProps(PayTestProps.withCallbacks(PayTestProps.keyMode(APP)))));
        plugin.start();
        plugin.start();

        assertNotNull(PayKit.defaultClient());

        plugin.stop();
        plugin.stop();

        PayException e = assertThrows(PayException.class, PayKit::defaultClient);
        assertEquals(NOT_INSTALLED, e.getMessage());
    }

    @Test
    public void shouldStartWithCertificateMode() {
        plugin = new PayPlugin(prop(channelProps(PayTestProps.certMode(APP))));
        plugin.start();

        assertNotNull("证书模式装配后应可注入客户端", Aop.get(PayClient.class));
        assertSame(Aop.get(PayClient.class), PayKit.defaultClient());
    }

    @Test
    public void shouldRejectUnknownChannelType() {
        Properties invalid = new Properties();
        invalid.setProperty("pay.default-channel", "main");
        invalid.setProperty("pay.main.type", "unknown");
        invalid.setProperty("pay.main.default-merchant-id", APP);
        plugin = new PayPlugin(prop(invalid));

        PayException e = assertThrows(PayException.class, plugin::start);

        assertTrue("消息应含非法 type", e.getMessage().contains("pay.main.type=unknown"));
    }

    private static Properties channelProps(Properties legacy) {
        Properties target = new Properties();
        String prefix = "pay.apps." + APP + ".";
        for (String key : legacy.stringPropertyNames()) {
            String value = legacy.getProperty(key);
            if (key.startsWith(prefix)) {
                target.setProperty("pay.main." + key.substring(prefix.length()), value);
            } else {
                target.setProperty(key, value);
            }
        }
        target.setProperty("pay.default-channel", "main");
        target.setProperty("pay.main.type", "alipay");
        target.setProperty("pay.main.default-merchant-id", APP);
        return target;
    }

    private static Prop prop(Properties properties) {
        Prop prop = new Prop();
        prop.getProperties().putAll(properties);
        return prop;
    }
}
