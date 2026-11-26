/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.aop.AopFactory;
import cn.aifei.aop.AopKit;
import cn.aifei.log.LogFactory;
import cn.aifei.util.Prop;
import cn.hg.aifei.pay.PayNotifyUtil;
import cn.hg.aifei.pay.aifei.PayPlugin;
import cn.hg.aifei.pay.internal.PayChannelConfigLoader;
import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.PayTypes;
import cn.hg.aifei.pay.internal.UnimplementedPayClient;
import cn.hg.aifei.pay.internal.channels.ChannelAdapters;
import cn.hg.aifei.pay.internal.channels.ChannelConfig;
import cn.hg.aifei.pay.internal.channels.ChannelConfigLoader;
import cn.hg.aifei.pay.internal.channels.ChannelVerifier;
import cn.hg.aifei.pay.model.PayNotifyRequest;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import com.ijpay.core.enums.SignType;
import com.ijpay.core.kit.WxPayKit;
import com.ijpay.fuiyou.FuiYouPayCore;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 七渠道落地门禁：白名单、按渠道必填键、适配器可构建、验签路径与装配合法性。
 */
public class PayChannelsTest {

    private static final List<String> CHANNELS = Arrays.asList(PayTypes.QQ, PayTypes.JDPAY, PayTypes.UNIONPAY,
            PayTypes.PAYPAL, PayTypes.STRIPE, PayTypes.FUIOU, PayTypes.XPAY);

    private AopFactory previousFactory;
    private LogFactory previousLogFactory;
    private PayPlugin plugin;

    @Before
    public void setUp() {
        previousFactory = AopKit.get().getAopFactory();
        AopKit.get().setAopFactory(new AopFactory());
        previousLogFactory = NoOpLogFactory.install();
        PayChannelRegistry.clear();
        plugin = null;
    }

    @After
    public void tearDown() {
        if (plugin != null) {
            plugin.stop();
        }
        PayChannelRegistry.clear();
        AopKit.get().setAopFactory(previousFactory);
        NoOpLogFactory.restore(previousLogFactory);
    }

    @Test
    public void whiteListAndImplementedState() {
        assertEquals("合法类型应为 10 个（含待落地的 wxpay）", 10, PayTypes.supported().size());
        assertEquals("已落地应为 9 个", 9, PayTypes.implemented().size());
        for (String channel : CHANNELS) {
            assertTrue("应支持 " + channel, PayTypes.isSupported(channel));
            assertTrue("应已落地 " + channel, PayTypes.isImplemented(channel));
        }
        assertTrue(PayTypes.isSupported(PayTypes.WXPAY));
        assertFalse("wxpay 本次不落地", PayTypes.isImplemented(PayTypes.WXPAY));
        assertFalse("无版本含义的 alipay 非法", PayTypes.isSupported("alipay"));
    }

    @Test
    public void requiredKeysAreChannelSpecific() {
        assertTrue(PayTypes.requiredKeys(PayTypes.QQ).contains("app-id"));
        assertTrue(PayTypes.requiredKeys(PayTypes.QQ).contains("partner-key"));
        assertTrue(PayTypes.requiredKeys(PayTypes.JDPAY).contains("des-key"));
        assertTrue(PayTypes.requiredKeys(PayTypes.JDPAY).contains("rsa-public-key"));
        assertTrue(PayTypes.requiredKeys(PayTypes.UNIONPAY).contains("gateway-url"));
        assertTrue(PayTypes.requiredKeys(PayTypes.PAYPAL).contains("secret"));
        assertTrue(PayTypes.requiredKeys(PayTypes.STRIPE).contains("api-key"));
        assertTrue(PayTypes.requiredKeys(PayTypes.FUIOU).contains("mchnt-key"));
        assertTrue(PayTypes.requiredKeys(PayTypes.XPAY).contains("server-url"));
        assertTrue(PayTypes.requiredKeys(PayTypes.XPAY).contains("app-id"));
    }

    @Test
    public void eachChannelBuildsRealAdapter() {
        for (String channel : CHANNELS) {
            Properties properties = channelProps(channel);
            PayChannelConfigLoader.ChannelSpec spec = PayChannelConfigLoader.load(properties).get(0);
            ChannelConfig config = ChannelConfigLoader.load(properties, spec.name(), spec.type(),
                    spec.merchantId());
            PayClient client = ChannelAdapters.createClient(config);
            assertFalse(channel + " 不得为占位实现", client instanceof UnimplementedPayClient);
            assertEquals(spec.name(), client.channelName());
            assertNotNull(ChannelAdapters.createVerifier(config));
        }
    }

    @Test
    public void qqVerifierAcceptsValidSignAndRejectsTamperedSign() {
        ChannelConfig config = config(PayTypes.QQ);
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("out_trade_no", "202611260001");
        params.put("total_fee", "1");
        params.put("appid", "app");
        params.put("sign", WxPayKit.createSign(params, "partner", SignType.MD5));
        ChannelVerifier verifier = ChannelAdapters.createVerifier(config);

        assertTrue(verifier.verify(null, params));
        params.put("sign", "tampered");
        assertFalse(verifier.verify(null, params));
    }

    @Test
    public void fuiouVerifierUsesMd5NotifySign() {
        ChannelConfig config = config(PayTypes.FUIOU);
        String[] values = {"mch001", "order001", "1", "1", "20261126000000", "20261126", "rand"};
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("mchnt_cd", values[0]);
        params.put("mchnt_order_no", values[1]);
        params.put("settle_order_amt", values[2]);
        params.put("order_amt", values[3]);
        params.put("txn_fin_ts", values[4]);
        params.put("reserved_fy_settle_dt", values[5]);
        params.put("random_str", values[6]);
        params.put("sign", FuiYouPayCore.createSign("mchnt-key", values));
        ChannelVerifier verifier = ChannelAdapters.createVerifier(config);

        assertTrue(verifier.verify(null, params));
        params.put("order_amt", "999");
        assertFalse(verifier.verify(null, params));
    }

    @Test
    public void pluginRegistersChannelWithoutCallingGateway() {
        Properties properties = channelProps(PayTypes.STRIPE);
        plugin = new PayPlugin(prop(properties));
        plugin.start();

        PayClient client = PayKit.use("channel");
        assertNotNull(client);
        assertFalse(client instanceof UnimplementedPayClient);
        assertEquals("channel", client.channelName());
        assertSame(client, PayKit.defaultClient());
        assertThrows(PayException.class, () -> PayKit.use("missing"));
    }

    @Test
    public void pluginRegistersMultipleNamedChannels() {
        Properties properties = new Properties();
        properties.setProperty("pay.default-channel", "qq-main");
        properties.setProperty("pay.qq-main.type", PayTypes.QQ);
        properties.setProperty("pay.qq-main.default-merchant-id", "merchant-qq");
        properties.setProperty("pay.qq-main.app-id", "app");
        properties.setProperty("pay.qq-main.partner-key", "partner");
        properties.setProperty("pay.stripe-main.type", PayTypes.STRIPE);
        properties.setProperty("pay.stripe-main.default-merchant-id", "merchant-stripe");
        properties.setProperty("pay.stripe-main.api-key", "sk_test_local");
        properties.setProperty("pay.fuiou-main.type", PayTypes.FUIOU);
        properties.setProperty("pay.fuiou-main.default-merchant-id", "merchant-fuiou");
        properties.setProperty("pay.fuiou-main.mchnt-key", "mchnt-key");
        plugin = new PayPlugin(prop(properties));
        plugin.start();

        PayClient qq = PayKit.use("qq-main");
        PayClient stripe = PayKit.use("stripe-main");
        PayClient fuiou = PayKit.use("fuiou-main");
        assertFalse(qq instanceof UnimplementedPayClient);
        assertFalse(stripe instanceof UnimplementedPayClient);
        assertFalse(fuiou instanceof UnimplementedPayClient);
        assertSame(qq, PayKit.defaultClient());
        assertNotSame(qq, stripe);
        assertNotSame(stripe, fuiou);
        assertEquals("qq-main", qq.channelName());
        assertEquals("stripe-main", stripe.channelName());
        assertEquals("fuiou-main", fuiou.channelName());
        assertTrue(PayKit.channels().containsAll(Arrays.asList("qq-main", "stripe-main", "fuiou-main")));
        assertThrows(PayException.class, () -> PayKit.use("missing"));
    }

    @Test
    public void notifyUtilSharesChannelVerifierCore() {
        ChannelConfig config = config(PayTypes.QQ);
        plugin = new PayPlugin(prop(channelProps(PayTypes.QQ)));
        plugin.start();

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("out_trade_no", "202611260002");
        params.put("total_fee", "1");
        params.put("appid", "app");
        params.put("sign", WxPayKit.createSign(params, "partner", SignType.MD5));

        boolean viaFacade = PayNotifyUtil.verify("channel", "merchant-001", PayNotifyRequest.of(params));
        boolean viaCore = ChannelAdapters.createVerifier(config).verify("merchant-001", params);
        assertTrue("公共入口应验签通过", viaFacade);
        assertTrue("共用核心应验签通过", viaCore);
        assertEquals("公共入口与共用核心结果应一致", viaCore, viaFacade);

        params.put("sign", "tampered");
        assertFalse("篡改后公共入口应拒绝",
                PayNotifyUtil.verify("channel", "merchant-001", PayNotifyRequest.of(params)));
    }

    private static ChannelConfig config(String type) {
        Properties properties = channelProps(type);
        PayChannelConfigLoader.ChannelSpec spec = PayChannelConfigLoader.load(properties).get(0);
        return ChannelConfigLoader.load(properties, spec.name(), spec.type(), spec.merchantId());
    }

    private static Properties channelProps(String type) {
        Properties properties = new Properties();
        properties.setProperty("pay.default-channel", "channel");
        properties.setProperty("pay.channel.type", type);
        properties.setProperty("pay.channel.default-merchant-id", "merchant-001");
        if (PayTypes.QQ.equals(type)) {
            properties.setProperty("pay.channel.app-id", "app");
            properties.setProperty("pay.channel.partner-key", "partner");
        } else if (PayTypes.JDPAY.equals(type)) {
            properties.setProperty("pay.channel.version", "1.0");
            properties.setProperty("pay.channel.rsa-private-key", "rsa-private");
            properties.setProperty("pay.channel.rsa-public-key", "rsa-public");
            properties.setProperty("pay.channel.des-key", "des");
        } else if (PayTypes.UNIONPAY.equals(type)) {
            properties.setProperty("pay.channel.gateway-url", "gateway");
            properties.setProperty("pay.channel.api-key", "api");
        } else if (PayTypes.PAYPAL.equals(type)) {
            properties.setProperty("pay.channel.client-id", "client");
            properties.setProperty("pay.channel.secret", "secret");
        } else if (PayTypes.STRIPE.equals(type)) {
            properties.setProperty("pay.channel.api-key", "sk_test_local");
        } else if (PayTypes.FUIOU.equals(type)) {
            properties.setProperty("pay.channel.mchnt-key", "mchnt-key");
        } else if (PayTypes.XPAY.equals(type)) {
            properties.setProperty("pay.channel.app-id", "app");
            properties.setProperty("pay.channel.api-key", "api");
            properties.setProperty("pay.channel.server-url", "gateway");
        }
        return properties;
    }

    private static Prop prop(Properties properties) {
        Prop prop = new Prop();
        prop.getProperties().putAll(properties);
        return prop;
    }
}
