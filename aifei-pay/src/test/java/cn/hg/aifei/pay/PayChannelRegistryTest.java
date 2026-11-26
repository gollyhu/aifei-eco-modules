/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.UnimplementedPayClient;
import cn.hg.aifei.pay.internal.UnimplementedPayVerifier;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 多通道注册：默认通道、命名通道、未知通道与未落地通道的可读失败。
 */
public class PayChannelRegistryTest {

    @Before
    public void setUp() {
        PayChannelRegistry.clear();
        PayChannelRegistry.register("alipay3", new UnimplementedPayClient("alipay3", "alipay3"),
                new UnimplementedPayVerifier());
        PayChannelRegistry.register("wxpay", new UnimplementedPayClient("wxpay", "wxpay"),
                new UnimplementedPayVerifier());
        PayChannelRegistry.registerDefault("alipay3");
        PayChannelRegistry.setStarted(true);
    }

    @After
    public void tearDown() {
        PayChannelRegistry.clear();
    }

    @Test
    public void shouldReturnDistinctNamedClients() {
        PayClient alipay = PayKit.use("alipay3");
        PayClient wxpay = PayKit.use("wxpay");

        assertSame("默认通道应为 alipay", alipay, PayKit.defaultClient());
        assertNotSame("命名通道应返回不同实例", alipay, wxpay);
        assertTrue("通道集合应含两个通道", PayKit.channels().containsAll(java.util.Arrays.asList("alipay3", "wxpay")));
        assertEquals("通道名", "wxpay", wxpay.channelName());
    }

    @Test
    public void shouldThrowForUnknownChannel() {
        PayException e = assertThrows(PayException.class, () -> PayKit.use("unknown"));

        assertEquals("支付通道不存在：unknown", e.getMessage());
    }

    @Test
    public void shouldReportUnimplementedChannel() {
        PayClient wxpay = PayKit.use("wxpay");

        PayException e = assertThrows(PayException.class, () -> wxpay.query(null, "T1"));

        assertTrue("消息应标明适配器未落地", e.getMessage().contains("适配器未落地"));
    }
}
