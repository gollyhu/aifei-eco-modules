/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.log.LogFactory;
import cn.hg.aifei.pay.model.PayNotifyResult;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestEnv;
import cn.hg.aifei.pay.support.PayTestProps;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 同步与异步两类回调报文（AC-12）：同步 {@code return_url} 的 query string 与异步 {@code notify_url} 的
 * form body 都是 {@code application/x-www-form-urlencoded}，在模块内收敛为同一个解析点。
 */
public class PayVerifyFormTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 换入前的日志工厂。
     */
    private LogFactory previousLogFactory;

    /**
     * 验签失败会记录一次 warn，需要把日志工厂换成空实现。
     */
    @Before
    public void silenceLogs() {
        previousLogFactory = NoOpLogFactory.install();
        PayTestEnv.install(PayTestProps.keyMode(APP));
    }

    /**
     * 清理门面持有器与日志工厂。
     */
    @After
    public void cleanup() {
        PayTestEnv.clear();
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * AC-12：同步 {@code return_url} 的 GET query string → 验签通过并解析出幂等键。
     *
     * @throws UnsupportedEncodingException 编码失败
     */
    @Test
    public void shouldVerifySyncReturnQueryString() throws UnsupportedEncodingException {
        Map<String, String> params = signedTradeNotify(PayTestCrypto.KEY_PAIR_OLD);
        String queryString = encode(params);

        assertTrue("同步返回的 query string 应验签通过", AliPayNotifyUtil.verify(APP, queryString));

        PayNotifyResult result = AliPayNotifyUtil.verifyAndParse(APP, queryString);
        assertEquals("应解析出商户订单号", "T2026010100001", result.outTradeNo());
        assertEquals("应解析出交易状态", "TRADE_SUCCESS", result.tradeStatus());
    }

    /**
     * AC-12：异步 {@code notify_url} 的 POST form body → 验签通过并解析出幂等键。
     *
     * @throws UnsupportedEncodingException 编码失败
     */
    @Test
    public void shouldVerifyAsyncNotifyFormBody() throws UnsupportedEncodingException {
        Map<String, String> params = signedTradeNotify(PayTestCrypto.KEY_PAIR_OLD);
        params.put("sign_type", "RSA2");

        assertTrue("异步通知的 form body 应验签通过",
                AliPayNotifyUtil.verify(APP, encode(params)));
    }

    /**
     * AC-12：同一份报文以 query string 与 form body 两种形态提交，解析结果完全一致（解析路径唯一）。
     *
     * @throws UnsupportedEncodingException 编码失败
     */
    @Test
    public void shouldParseBothFormsToSameResult() throws UnsupportedEncodingException {
        Map<String, String> params = signedTradeNotify(PayTestCrypto.KEY_PAIR_OLD);
        String encoded = encode(params);

        PayNotifyResult fromBody = AliPayNotifyUtil.verifyAndParse(APP, encoded);
        PayNotifyResult fromMap = AliPayNotifyUtil.verifyAndParse(APP, params);

        assertEquals(fromMap.outTradeNo(), fromBody.outTradeNo());
        assertEquals(fromMap.tradeNo(), fromBody.tradeNo());
        assertEquals(fromMap.tradeStatus(), fromBody.tradeStatus());
        assertEquals(fromMap.totalAmount(), fromBody.totalAmount());
        assertEquals(fromMap.appId(), fromBody.appId());
    }

    /**
     * AC-12：报文先按 UTF-8 解码再验签（含中文与需要转义的字符）。
     *
     * @throws UnsupportedEncodingException 编码失败
     */
    @Test
    public void shouldDecodeUrlEncodedValuesBeforeVerifying() throws UnsupportedEncodingException {
        Map<String, String> params = tradeNotify();
        params.put("subject", "测试订单 含空格&符号=等号");
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);
        String encoded = encode(params);

        assertTrue("转义后的报文应先解码再验签", AliPayNotifyUtil.verify(APP, encoded));

        PayNotifyResult result = AliPayNotifyUtil.verifyAndParse(APP, encoded);
        assertEquals("解析结果应还原中文与空格", "测试订单 含空格&符号=等号", result.params().get("subject"));
    }

    /**
     * AC-12：表单报文被篡改 → 验签失败（失败不静默）。
     *
     * @throws UnsupportedEncodingException 编码失败
     */
    @Test
    public void shouldRejectTamperedFormBody() throws UnsupportedEncodingException {
        Map<String, String> params = signedTradeNotify(PayTestCrypto.KEY_PAIR_OLD);
        String tampered = encode(params).replace("TRADE_SUCCESS", "TRADE_FINISHED");

        assertFalse("被篡改的表单报文必须验签失败", AliPayNotifyUtil.verify(APP, tampered));
    }

    /**
     * AC-12：非法转义序列的报文属调用方写错（不是验签结论），抛 {@link IllegalArgumentException}。
     */
    @Test
    public void shouldRejectMalformedFormBody() {
        assertThrows(IllegalArgumentException.class,
                () -> AliPayNotifyUtil.verify(APP, "out_trade_no=%zz&sign=abc"));
    }

    /**
     * AC-12：报文为 {@code null} → 抛 {@link AliPayException}（不误判为验签通过）。
     */
    @Test
    public void shouldThrowWhenFormBodyNull() {
        AliPayException e = assertThrows(AliPayException.class,
                () -> AliPayNotifyUtil.verify(APP, (String) null));

        assertTrue("消息应为验签失败：" + e.getMessage(), e.getMessage().contains("验签失败"));
    }

    /**
     * 构造带签名的交易类通知。
     *
     * @param signer 签名密钥对
     * @return 报文参数
     */
    private static Map<String, String> signedTradeNotify(java.security.KeyPair signer) {
        Map<String, String> params = tradeNotify();
        PayTestCrypto.signNotify(params, signer);
        return params;
    }

    /**
     * 构造未签名的交易类通知。
     *
     * @return 报文参数
     */
    private static Map<String, String> tradeNotify() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("notify_time", "2026-11-26 10:00:00");
        params.put("notify_type", "trade_status_sync");
        params.put("notify_id", "ac05099524730693a8b330c5ecf72da9786");
        params.put("app_id", APP);
        params.put("charset", "utf-8");
        params.put("version", "1.0");
        params.put("out_trade_no", "T2026010100001");
        params.put("trade_no", "2026010122001445101000000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "0.01");
        params.put("subject", "测试订单");
        return params;
    }

    /**
     * 把参数编码成 {@code application/x-www-form-urlencoded} 报文。
     *
     * @param params 报文参数
     * @return 编码后的报文
     * @throws UnsupportedEncodingException 编码失败
     */
    private static String encode(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return body.toString();
    }
}
