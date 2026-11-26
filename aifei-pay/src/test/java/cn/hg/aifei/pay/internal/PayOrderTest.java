/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestProps;
import cn.aifei.log.LogFactory;
import java.util.LinkedHashMap;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 下单动作（AC-06 扫码 / AC-07 H5 与 PC）：动作语义、参数校验、金额换算与载荷类型规则。
 *
 * <p>内核调用以 {@link PayKernelStub} 替身注入，用例断言「交给内核的报文」与「返回给调用方的值对象」，
 * 全程零联网。</p>
 */
public class PayOrderTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 商户订单号。
     */
    private static final String OUT_TRADE_NO = "T2026010100001";

    /**
     * 换入前的日志工厂。
     */
    private LogFactory previousLogFactory;

    /**
     * 内核替身。
     */
    private PayKernelStub stub;

    /**
     * 被测客户端。
     */
    private DefaultPayClient client;

    /**
     * 装配默认配置（普通公钥模式 + 全局回调地址）与伪内核。
     */
    @Before
    public void setUp() {
        previousLogFactory = NoOpLogFactory.install();
        stub = new PayKernelStub();
        client = new DefaultPayClient(PayConfig.parse(
                PayTestProps.withCallbacks(PayTestProps.keyMode(APP))), stub);
    }

    /**
     * 还原日志工厂。
     */
    @After
    public void tearDown() {
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * AC-06：扫码下单返回二维码载荷，类型为 {@code QRCODE}，回显商户订单号。
     */
    @Test
    public void shouldReturnQrcodePayloadForPrecreate() {
        PayOrderResult result = client.precreate(APP, OUT_TRADE_NO, 1L, "测试订单", null);

        assertEquals("渠道应为当面付扫码", PayOrderResult.Channel.QRCODE, result.channel());
        assertEquals("载荷类型应为二维码", PayOrderResult.PayloadType.QRCODE, result.payloadType());
        assertEquals("载荷应为内核返回的二维码内容", "QR-CONTENT-T2026010100001", result.payload());
        assertEquals("应回显商户订单号", OUT_TRADE_NO, result.outTradeNo());
    }

    /**
     * AC-06：金额按「分 → 元」换算后交给内核，回调地址用全局默认值。
     */
    @Test
    public void shouldConvertAmountAndUseGlobalNotifyUrlForPrecreate() {
        client.precreate(APP, OUT_TRADE_NO, 12345L, "测试订单", null);

        PayKernelStub.Call call = stub.lastCall();
        assertEquals("precreate", call.action());
        assertEquals("金额应换算为元字符串", "123.45", call.param("total_amount"));
        assertEquals("应使用全局默认异步回调地址", PayTestProps.NOTIFY_URL, call.param("notify_url"));
        assertEquals("商户订单号应原样透传", OUT_TRADE_NO, call.param("out_trade_no"));
        assertEquals("标题应原样透传", "测试订单", call.param("subject"));
        assertEquals("当面付产品码应固定", "FACE_TO_FACE_PAYMENT", call.param("product_code"));
        assertEquals("应用配置应为解析后的沙箱应用", APP, call.app().appId());
    }

    /**
     * AC-06：显式传入的回调地址优先于全局默认值。
     */
    @Test
    public void shouldPreferExplicitNotifyUrlForPrecreate() {
        String explicit = "https" + "://" + "biz.example.com/pay/notify";

        client.precreate(APP, OUT_TRADE_NO, 1L, "测试订单", explicit);

        assertEquals(explicit, stub.lastCall().param("notify_url"));
    }

    /**
     * AC-07：金额必须大于 0，0 与负数属调用方写错。
     */
    @Test
    public void shouldRejectNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> client.precreate(APP, OUT_TRADE_NO, 0L, "测试订单", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.precreate(APP, OUT_TRADE_NO, -1L, "测试订单", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.wapPay(APP, OUT_TRADE_NO, 0L, "测试订单", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.pagePay(APP, OUT_TRADE_NO, -100L, "测试订单", null, null));
        assertTrue("校验失败不应触达内核", stub.callCount() == 0);
    }

    /**
     * AC-07：商户订单号与标题必填且有长度上限。
     */
    @Test
    public void shouldRejectBlankOrOverlongTextParameters() {
        assertThrows(IllegalArgumentException.class, () -> client.precreate(APP, "  ", 1L, "测试订单", null));
        assertThrows(IllegalArgumentException.class, () -> client.precreate(APP, null, 1L, "测试订单", null));
        assertThrows(IllegalArgumentException.class, () -> client.precreate(APP, OUT_TRADE_NO, 1L, " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.precreate(APP, repeat('N', 65), 1L, "测试订单", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.precreate(APP, OUT_TRADE_NO, 1L, repeat('S', 257), null));
    }

    /**
     * AC-06：{@code precreate} 缺少异步回调地址（入参与全局配置都没有）→ 抛 {@link PayException}。
     */
    @Test
    public void shouldFailWhenNotifyUrlMissingForPrecreate() {
        DefaultPayClient noCallbackClient = new DefaultPayClient(
                PayConfig.parse(PayTestProps.keyMode(APP)), stub);

        PayException e = assertThrows(PayException.class,
                () -> noCallbackClient.precreate(APP, OUT_TRADE_NO, 1L, "测试订单", null));

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("pay.notify-url"));
    }

    /**
     * AC-07：默认 {@code pay.page-http-method=POST} → 载荷类型为自提交表单。
     */
    @Test
    public void shouldReturnFormPayloadWhenPageHttpMethodIsPost() {
        PayOrderResult result = client.wapPay(APP, OUT_TRADE_NO, 1L, "测试订单", null, null);

        assertEquals("H5 渠道", PayOrderResult.Channel.WAP, result.channel());
        assertEquals("POST 方式应返回表单载荷类型", PayOrderResult.PayloadType.FORM, result.payloadType());
        assertEquals("载荷应为内核返回的表单 HTML", "<form name=\"punchout_form\" method=\"post\"></form>",
                result.payload());
    }

    /**
     * AC-07：{@code pay.page-http-method=GET} → 载荷类型为可跳转 URL。
     */
    @Test
    public void shouldReturnUrlPayloadWhenPageHttpMethodIsGet() {
        Properties properties = PayTestProps.withCallbacks(PayTestProps.with(PayTestProps.keyMode(APP),
                "pay.page-http-method", "GET"));
        DefaultPayClient getClient = new DefaultPayClient(PayConfig.parse(properties), stub);
        stub.pagePayload("https" + "://" + "openapi.alipay.com/gateway.do?alipay_sdk=trade");

        PayOrderResult result = getClient.wapPay(APP, OUT_TRADE_NO, 1L, "测试订单", null, null);

        assertEquals("GET 方式应返回 URL 载荷类型", PayOrderResult.PayloadType.URL, result.payloadType());
        assertEquals("载荷应为内核返回的跳转地址",
                "https" + "://" + "openapi.alipay.com/gateway.do?alipay_sdk=trade", result.payload());
        assertEquals("应把 GET 传给内核", "GET", stub.lastCall().httpMethod());
    }

    /**
     * AC-07：PC 下单渠道为 {@code PAGE}，接口方法名为 {@code alipay.trade.page.pay}。
     */
    @Test
    public void shouldReturnPageChannelForPagePay() {
        PayOrderResult result = client.pagePay(APP, OUT_TRADE_NO, 100L, "测试订单", null, null);

        assertEquals("PC 渠道", PayOrderResult.Channel.PAGE, result.channel());
        assertEquals("PC 接口方法名", "alipay.trade.page.pay", stub.lastCall().method());
        assertEquals("默认 POST 方式", "POST", stub.lastCall().httpMethod());
    }

    /**
     * AC-07：H5 下单的接口方法名为 {@code alipay.trade.wap.pay}。
     */
    @Test
    public void shouldUseWapMethodForWapPay() {
        client.wapPay(APP, OUT_TRADE_NO, 1L, "测试订单", null, null);

        assertEquals("H5 接口方法名", "alipay.trade.wap.pay", stub.lastCall().method());
    }

    /**
     * AC-07：页面下单把业务参数放进 {@code biz_content}，回调地址作为公共参数平铺，并带退出地址。
     */
    @Test
    public void shouldPassBizContentAndCallbacksForPagePay() {
        client.pagePay(APP, OUT_TRADE_NO, 100L, "测试订单", null, null);

        PayKernelStub.Call call = stub.lastCall();
        assertEquals("同步跳转地址应平铺", PayTestProps.RETURN_URL, call.param("return_url"));
        assertEquals("异步回调地址应平铺", PayTestProps.NOTIFY_URL, call.param("notify_url"));
        assertEquals("业务参数：订单号", OUT_TRADE_NO, call.bizContentParam("out_trade_no"));
        assertEquals("业务参数：金额", "1.00", call.bizContentParam("total_amount"));
        assertEquals("业务参数：标题", "测试订单", call.bizContentParam("subject"));
        assertEquals("业务参数：PC 产品码", "FAST_INSTANT_TRADE_PAY", call.bizContentParam("product_code"));
        assertEquals("业务参数：退出地址", PayTestProps.RETURN_URL, call.bizContentParam("quit_url"));
    }

    /**
     * AC-07：H5 缺少同步回调地址（入参与全局配置都没有）→ 抛 {@link PayException}。
     */
    @Test
    public void shouldFailWhenReturnUrlMissingForWapPay() {
        Properties notifyOnly = PayTestProps.with(PayTestProps.keyMode(APP),
                "pay.notify-url", PayTestProps.NOTIFY_URL);
        DefaultPayClient noCallbackClient = new DefaultPayClient(PayConfig.parse(notifyOnly), stub);

        PayException e = assertThrows(PayException.class,
                () -> noCallbackClient.wapPay(APP, OUT_TRADE_NO, 1L, "测试订单", null, null));

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("pay.return-url"));
    }

    /**
     * AC-06 / AC-07：内核响应缺字段时载荷归一为空串而非 {@code null}（值对象契约）。
     */
    @Test
    public void shouldNormalizeMissingPayloadToEmptyString() {
        stub.precreateResponse(new LinkedHashMap<String, Object>());

        PayOrderResult result = client.precreate(APP, OUT_TRADE_NO, 1L, "测试订单", null);

        assertEquals("缺失载荷应为空串", "", result.payload());
        assertEquals("缺失交易号应为空串", "", result.tradeNo());
        assertEquals("订单号回退为请求值", OUT_TRADE_NO, result.outTradeNo());
    }

    /**
     * 生成重复字符的字符串。
     *
     * @param ch    字符
     * @param count 重复次数
     * @return 字符串
     */
    private static String repeat(char ch, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }
}
