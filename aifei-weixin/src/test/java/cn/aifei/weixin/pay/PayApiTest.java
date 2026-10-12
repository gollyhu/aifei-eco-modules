/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.WxPayConfig;
import cn.aifei.weixin.pay.core.PayHttpResponse;
import cn.aifei.weixin.support.FakePayHttpKit;
import cn.aifei.weixin.support.PayTestKeys;
import com.alibaba.fastjson2.JSONObject;
import java.security.KeyPair;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;

/**
 * P1：下单 / 查单 / 关单 / 退款的路径、Authorization 与报文、错误语义
 */
public class PayApiTest {

    private KeyPair pair;
    private PayMerchant merchant;
    private FakePayHttpKit http;

    @Before
    public void setUp() {
        pair = PayTestKeys.generateKeyPair();
        merchant = PayMerchant.of(PayTestKeys.MCH_ID, pair.getPrivate(), PayTestKeys.MCH_SERIAL,
                PayTestKeys.API_V3_KEY);
        http = new FakePayHttpKit();
        WxPayConfig.set(http, serial -> pair.getPublic());
    }

    @Test
    public void jsapiPostsOfficialBody() {
        http.postResponse = new PayHttpResponse(200, new HashMap<String, String>(),
                "{\"prepay_id\":\"wx20141027200939\"}");

        JSONObject result = PayApi.jsapi(merchant, PayTestKeys.APP_ID, "VIP订阅",
                "ORDER-1", "https://demo/pay/notify/100000", 100, "oABC");

        assertEquals("wx20141027200939", result.getString("prepay_id"));
        assertEquals("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi", http.postUrls.get(0));
        assertTrue(http.postHeaders.get(0).get("Authorization")
                .startsWith("WECHATPAY2-SHA256-RSA2048 mchid=\"" + PayTestKeys.MCH_ID + "\""));
        assertEquals("application/json", http.postHeaders.get(0).get("Content-Type"));

        JSONObject body = JSONObject.parseObject(http.postBodies.get(0));
        assertEquals(PayTestKeys.APP_ID, body.getString("appid"));
        assertEquals(PayTestKeys.MCH_ID, body.getString("mchid"));
        assertEquals("ORDER-1", body.getString("out_trade_no"));
        assertEquals(100, body.getJSONObject("amount").getIntValue("total"));
        assertEquals("CNY", body.getJSONObject("amount").getString("currency"));
        assertEquals("oABC", body.getJSONObject("payer").getString("openid"));
    }

    @Test
    public void queryUsesGetWithMchidQuery() {
        http.getResponse = new PayHttpResponse(200, new HashMap<String, String>(),
                "{\"trade_state\":\"SUCCESS\"}");

        JSONObject result = PayApi.queryByOutTradeNo(merchant, "ORDER-1");

        assertEquals("SUCCESS", result.getString("trade_state"));
        assertEquals("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/ORDER-1?mchid="
                + PayTestKeys.MCH_ID, http.getUrls.get(0));
        assertTrue(http.getHeaders.get(0).get("Authorization").startsWith("WECHATPAY2-SHA256-RSA2048"));
    }

    @Test
    public void closeToleratesEmpty204Body() {
        http.postResponse = new PayHttpResponse(204, new HashMap<String, String>(), "");

        PayApi.close(merchant, "ORDER-1");

        assertEquals("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/ORDER-1/close",
                http.postUrls.get(0));
        assertEquals("{\"mchid\":\"" + PayTestKeys.MCH_ID + "\"}", http.postBodies.get(0));
    }

    @Test
    public void refundPostsAmountAndRefundNo() {
        PayApi.refund(merchant, "ORDER-1", "REFUND-1", 50, 100,
                "https://demo/refund/notify/100000");

        assertEquals("https://api.mch.weixin.qq.com/v3/refund/domestic/refunds", http.postUrls.get(0));
        JSONObject body = JSONObject.parseObject(http.postBodies.get(0));
        assertEquals("ORDER-1", body.getString("out_trade_no"));
        assertEquals("REFUND-1", body.getString("out_refund_no"));
        assertEquals(50, body.getJSONObject("amount").getIntValue("refund"));
        assertEquals(100, body.getJSONObject("amount").getIntValue("total"));
    }

    @Test
    public void non2xxThrowsWithOfficialCodeAndMessage() {
        http.postResponse = new PayHttpResponse(400, new HashMap<String, String>(),
                "{\"code\":\"PARAM_ERROR\",\"message\":\"无效的 openid\"}");

        try {
            PayApi.jsapi(merchant, PayTestKeys.APP_ID, "VIP订阅", "ORDER-1",
                    "https://demo/notify/100000", 100, "oABC");
            fail();
        } catch (WxApiException e) {
            assertTrue(e.getMessage().contains("400"));
            assertTrue(e.getMessage().contains("PARAM_ERROR"));
            assertTrue(e.getMessage().contains("无效的 openid"));
        }
    }

    @Test
    public void signatureCoversRequestPathAndBody() {
        http.postResponse = new PayHttpResponse(200, new HashMap<String, String>(), "{\"prepay_id\":\"p1\"}");
        PayApi.jsapi(merchant, PayTestKeys.APP_ID, "d", "ORDER-1", "https://demo/notify/100000", 1, "o");

        String authorization = http.postHeaders.get(0).get("Authorization");
        String message = "POST\n/v3/pay/transactions/jsapi\n"
                + extract(authorization, "timestamp") + "\n"
                + extract(authorization, "nonce_str") + "\n"
                + http.postBodies.get(0) + "\n";
        assertTrue(cn.aifei.weixin.pay.PaySignatureTest.verifyWith(
                pair.getPublic(), message, extract(authorization, "signature")));
    }

    private static String extract(String authorization, String param) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(param + "=\"([^\"]+)\"").matcher(authorization);
        assertTrue("缺少参数 " + param, matcher.find());
        return matcher.group(1);
    }
}
