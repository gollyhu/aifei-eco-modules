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

import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.internal.PayHolder;
import cn.hg.aifei.pay.internal.PayVerifier;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import cn.hg.aifei.pay.support.PayTestProps;
import java.net.URI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 静态门面（AC-01）：{@link AliPayKit#client()} 返回与 {@code @Inject} 相同的实例；未装配 / 已停止时
 * 抛固定消息的 {@link AliPayException}；两个网关常量是配置校验的唯一取值来源（AC-15 的基准）。
 */
public class AliPayKitTest {

    /**
     * 未装配 / 已停止的固定消息。
     */
    private static final String NOT_INSTALLED = "支付宝客户端未装配或已停止";

    /**
     * 模拟被装配的客户端：门面用例不执行任何交易动作。
     */
    private final AliPayClient client = new UnsupportedAliPayClient();

    /**
     * 清空门面持有器，保证用例从「未装配」开始。
     */
    @Before
    public void clearBefore() {
        PayHolder.clear();
    }

    /**
     * 清空门面持有器，避免影响其它测试类。
     */
    @After
    public void clearAfter() {
        PayHolder.clear();
    }

    /**
     * AC-01：未装配时调用门面 → 抛固定消息的 {@link AliPayException}，不静默返回 {@code null}。
     */
    @Test
    public void shouldThrowWhenNotInstalled() {
        AliPayException e = assertThrows(AliPayException.class, AliPayKit::client);

        assertEquals(NOT_INSTALLED, e.getMessage());
    }

    /**
     * AC-01：装配后门面返回的就是被装配的实例（与容器注入的是同一个对象）。
     */
    @Test
    public void shouldReturnInstalledClient() {
        install(client);

        assertSame("门面应返回被装配的同一实例", client, AliPayKit.client());
    }

    /**
     * AC-01：停止（清空）后门面再次调用即失败。
     */
    @Test
    public void shouldFailAfterStop() {
        install(client);

        PayHolder.clear();

        AliPayException e = assertThrows(AliPayException.class, AliPayKit::client);
        assertEquals(NOT_INSTALLED, e.getMessage());
    }

    /**
     * AC-15：两个网关常量指向官方网关，且沙箱与生产 host 不同（环境一致性校验的基准）。
     *
     * @throws Exception URI 解析失败
     */
    @Test
    public void shouldExposeOfficialGateways() throws Exception {
        URI sandbox = new URI(AliPayKit.GATEWAY_SANDBOX);
        URI production = new URI(AliPayKit.GATEWAY_PRODUCTION);

        assertEquals("沙箱网关 scheme", "https", sandbox.getScheme());
        assertEquals("沙箱网关 host", "openapi-sandbox.dl.alipaydev.com", sandbox.getHost());
        assertEquals("沙箱网关路径", "/gateway.do", sandbox.getPath());
        assertEquals("生产网关 scheme", "https", production.getScheme());
        assertEquals("生产网关 host", "openapi.alipay.com", production.getHost());
        assertEquals("生产网关路径", "/gateway.do", production.getPath());
        assertFalse("两个网关 host 必须不同", sandbox.getHost().equalsIgnoreCase(production.getHost()));
    }

    /**
     * 装配门面持有器（验签器用默认配置构建，本类不触发验签）。
     *
     * @param aliPayClient 待装配的客户端
     */
    private static void install(AliPayClient aliPayClient) {
        PayConfig config = PayConfig.parse(PayTestProps.keyMode(PayTestProps.APP_MAIN));
        PayHolder.install(aliPayClient, new PayVerifier(config.apps()));
    }

    /**
     * 门面用例专用的客户端：任何交易动作都直接失败，避免用例误触。
     */
    private static final class UnsupportedAliPayClient implements AliPayClient {

        @Override
        public PayOrderResult precreate(String appId, String outTradeNo, long amountFen, String subject,
                                        String notifyUrl) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public PayOrderResult wapPay(String appId, String outTradeNo, long amountFen, String subject,
                                     String returnUrl, String notifyUrl) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public PayOrderResult pagePay(String appId, String outTradeNo, long amountFen, String subject,
                                      String returnUrl, String notifyUrl) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public PayQueryResult query(String appId, String outTradeNo) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }

        @Override
        public PayRefundResult refund(String appId, String outTradeNo, String outRequestNo, long refundFen,
                                      String refundReason) {
            throw new UnsupportedOperationException("门面用例不执行交易");
        }
    }
}
