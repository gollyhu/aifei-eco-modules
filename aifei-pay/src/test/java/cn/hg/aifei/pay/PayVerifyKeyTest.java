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
import static org.junit.Assert.fail;

import cn.aifei.log.LogFactory;
import cn.hg.aifei.pay.spi.PayNotify;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestEnv;
import cn.hg.aifei.pay.support.PayTestProps;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 普通公钥方式的回调验签（AC-10）与凭据轮换（AC-13）。
 *
 * <p><b>固定向量</b>：报文由 {@link PayTestCrypto} 用测试私钥做 {@code SHA256withRSA} 签名得到，
 * 验签侧使用模块的 {@code PayNotifyUtil}。签名侧是独立实现（不复用被测代码），因此断言有效。</p>
 */
public class PayVerifyKeyTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 商户订单号（同时是交易幂等键）。
     */
    private static final String OUT_TRADE_NO = "T2026010100001";

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
     * AC-10：合法签名的异步通知 → 验签通过，且解析结果透出交易幂等键。
     */
    @Test
    public void shouldVerifyValidSignedNotify() {
        PayTestEnv.install(PayTestProps.keyMode(APP));
        Map<String, String> params = signedNotify(PayTestCrypto.KEY_PAIR_OLD);

        assertTrue("合法签名应通过", PayNotifyUtil.verify(APP, params));

        PayNotify result = PayNotifyUtil.verifyAndParse(APP, params);
        assertEquals("应为交易类通知", PayNotify.Type.TRADE, result.type());
        assertEquals("应透出交易幂等键", OUT_TRADE_NO, result.outTradeNo());
        assertEquals("应透出交易号", "2026010122001445101000000001", result.tradeNo());
        assertEquals("应透出交易状态", "TRADE_SUCCESS", result.tradeStatus());
        assertEquals("应透出订单金额", "0.01", result.totalAmount());
        assertEquals("应透出 appId", APP, result.appId());
    }

    /**
     * AC-10：签名后篡改任一字段 → 验签失败；{@code verifyAndParse} 抛异常（失败不静默）。
     */
    @Test
    public void shouldRejectTamperedField() {
        PayTestEnv.install(PayTestProps.keyMode(APP));
        Map<String, String> params = signedNotify(PayTestCrypto.KEY_PAIR_OLD);
        params.put("total_amount", "9999.00");

        assertFalse("篡改金额必须验签失败", PayNotifyUtil.verify(APP, params));

        try {
            PayNotifyUtil.verifyAndParse(APP, params);
            fail("验签失败必须抛 PayException");
        } catch (PayException e) {
            assertTrue("消息应含 appId：" + e.getMessage(), e.getMessage().contains(APP));
            assertTrue("消息应指出验签失败：" + e.getMessage(), e.getMessage().contains("验签失败"));
            assertFalse("消息不得含报文内容：" + e.getMessage(), e.getMessage().contains("9999.00"));
        }
    }

    /**
     * AC-10：报文缺 {@code sign} → 视为验签失败（不静默放过）。
     */
    @Test
    public void shouldRejectMissingSign() {
        PayTestEnv.install(PayTestProps.keyMode(APP));
        Map<String, String> params = tradeNotify();
        params.put("sign_type", "RSA2");

        assertFalse("缺签名必须失败", PayNotifyUtil.verify(APP, params));
    }

    /**
     * AC-10：{@code sign_type} 只支持 RSA2，其它取值一律失败（内核限制，不得静默放过）。
     */
    @Test
    public void shouldRejectUnsupportedSignType() {
        PayTestEnv.install(PayTestProps.keyMode(APP));
        Map<String, String> params = tradeNotify();
        params.put("sign", PayTestCrypto.sign(params, PayTestCrypto.KEY_PAIR_OLD));
        params.put("sign_type", "RSA");

        assertFalse("RSA（SHA1）不受支持，必须失败", PayNotifyUtil.verify(APP, params));
    }

    /**
     * AC-16 支撑：未配置的 appId 验签 → 抛 {@link PayException}（消息含该 appId）。
     */
    @Test
    public void shouldThrowWhenAppIdUnknown() {
        PayTestEnv.install(PayTestProps.keyMode(APP));
        Map<String, String> params = signedNotify(PayTestCrypto.KEY_PAIR_OLD);

        PayException e = assertThrows(PayException.class,
                () -> PayNotifyUtil.verify("2021999999999999", params));

        assertTrue("消息应含 appId：" + e.getMessage(), e.getMessage().contains("2021999999999999"));
    }

    /**
     * 报文参数为 {@code null} 属调用方写错 → 抛 {@link PayException}。
     */
    @Test
    public void shouldThrowWhenParamsNull() {
        PayTestEnv.install(PayTestProps.keyMode(APP));

        PayException e = assertThrows(PayException.class,
                () -> PayNotifyUtil.verify(APP, (Map<String, String>) null));

        assertTrue("消息应为验签失败：" + e.getMessage(), e.getMessage().contains("验签失败"));
    }

    /**
     * 未装配（或已停止）→ 抛固定消息的 {@link PayException}。
     */
    @Test
    public void shouldThrowWhenNotInstalled() {
        Map<String, String> params = signedNotify(PayTestCrypto.KEY_PAIR_OLD);

        PayException e = assertThrows(PayException.class, () -> PayNotifyUtil.verify(APP, params));

        assertEquals("支付客户端未装配或已停止", e.getMessage());
    }

    /**
     * AC-13：同一 appId 配置新旧两个公钥，用任一私钥签名的报文都能通过（轮换期并存）。
     */
    @Test
    public void shouldVerifyWithEitherOldOrNewKeyWhenBothConfigured() {
        PayTestEnv.install(rotationProps());

        assertTrue("旧凭据签名应通过", PayNotifyUtil.verify(APP, signedNotify(PayTestCrypto.KEY_PAIR_OLD)));
        assertTrue("新凭据签名应通过", PayNotifyUtil.verify(APP, signedNotify(PayTestCrypto.KEY_PAIR_NEW)));
    }

    /**
     * AC-13：只配置新凭据时，旧凭据签名的报文必须失败（移除旧凭据立即失效）。
     */
    @Test
    public void shouldRejectOldKeySignatureWhenOnlyNewKeyConfigured() {
        PayTestEnv.install(PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "verify.0.value"), PayTestCrypto.PUBLIC_KEY_NEW));

        assertFalse("旧凭据已移除，旧签名必须失败",
                PayNotifyUtil.verify(APP, signedNotify(PayTestCrypto.KEY_PAIR_OLD)));
        assertTrue("新凭据签名应通过",
                PayNotifyUtil.verify(APP, signedNotify(PayTestCrypto.KEY_PAIR_NEW)));
    }

    /**
     * 轮换期配置：verify.0 为旧公钥，verify.1 为新公钥。
     *
     * @return 配置源
     */
    private static Properties rotationProps() {
        return PayTestProps.with(PayTestProps.keyMode(APP),
                PayTestProps.key(APP, "verify.1.type"), "key",
                PayTestProps.key(APP, "verify.1.value"), PayTestCrypto.PUBLIC_KEY_NEW);
    }

    /**
     * 构造一笔带合法签名的交易类异步通知。
     *
     * @param signer 签名用的密钥对（模拟支付宝侧私钥）
     * @return 报文参数
     */
    private static Map<String, String> signedNotify(java.security.KeyPair signer) {
        Map<String, String> params = tradeNotify();
        PayTestCrypto.signNotify(params, signer);
        return params;
    }

    /**
     * 构造一笔未签名的交易类异步通知。
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
        params.put("out_trade_no", OUT_TRADE_NO);
        params.put("trade_no", "2026010122001445101000000001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "0.01");
        params.put("subject", "测试订单");
        return params;
    }
}
