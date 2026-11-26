/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.UnimplementedPayVerifier;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.net.URI;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 静态门面：默认通道、命名通道与注册表状态。
 */
public class PayKitTest {

    private static final String NOT_INSTALLED = "支付客户端未装配或已停止";
    private static final String CHANNEL = "alipay";

    private final PayClient client = new UnsupportedPayClient();

    @Before
    public void clearBefore() {
        PayChannelRegistry.clear();
    }

    @After
    public void clearAfter() {
        PayChannelRegistry.clear();
    }

    @Test
    public void shouldThrowWhenNotInstalled() {
        PayException e = assertThrows(PayException.class, PayKit::defaultClient);
        assertEquals(NOT_INSTALLED, e.getMessage());
    }

    @Test
    public void shouldReturnInstalledClient() {
        install(client, CHANNEL);

        assertSame("门面应返回被装配的同一实例", client, PayKit.defaultClient());
        assertSame("命名通道应返回同一实例", client, PayKit.use(CHANNEL));
        assertTrue("通道集合应含已装配通道", PayKit.channels().contains(CHANNEL));
    }

    @Test
    public void shouldThrowForUnknownChannel() {
        install(client, CHANNEL);

        PayException e = assertThrows(PayException.class, () -> PayKit.use("unknown"));
        assertEquals("支付通道不存在：unknown", e.getMessage());
    }

    @Test
    public void shouldFailAfterStop() {
        install(client, CHANNEL);
        PayChannelRegistry.clear();

        PayException e = assertThrows(PayException.class, PayKit::defaultClient);
        assertEquals(NOT_INSTALLED, e.getMessage());
    }

    @Test
    public void shouldExposeOfficialGateways() throws Exception {
        URI sandbox = new URI(PayKit.GATEWAY_SANDBOX);
        URI production = new URI(PayKit.GATEWAY_PRODUCTION);

        assertEquals("沙箱网关 scheme", "https", sandbox.getScheme());
        assertEquals("沙箱网关 host", "openapi-sandbox.dl.alipaydev.com", sandbox.getHost());
        assertEquals("生产网关 host", "openapi.alipay.com", production.getHost());
        assertFalse("两个网关 host 必须不同", sandbox.getHost().equalsIgnoreCase(production.getHost()));
    }

    private static void install(PayClient payClient, String name) {
        PayChannelRegistry.clear();
        PayChannelRegistry.register(name, payClient, new UnimplementedPayVerifier());
        PayChannelRegistry.registerDefault(name);
        PayChannelRegistry.setStarted(true);
    }

    private static final class UnsupportedPayClient implements PayClient {

        @Override
        public PayOrderResult createOrder(String merchantId, String outTradeNo, long amountFen, String subject,
                                          String payType, String returnUrl, String notifyUrl,
                                          Map<String, String> options) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public PayQueryResult query(String merchantId, String outTradeNo) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public PayRefundResult refund(String merchantId, String outTradeNo, String outRequestNo, long refundFen,
                                      String refundReason) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public String channelName() {
            return CHANNEL;
        }
    }
}
