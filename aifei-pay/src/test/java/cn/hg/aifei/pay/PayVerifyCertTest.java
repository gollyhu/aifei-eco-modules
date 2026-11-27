/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.hg.aifei.pay.internal.PayAppConfig;
import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.spi.PayNotify;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestEnv;
import cn.hg.aifei.pay.support.PayTestProps;
import cn.aifei.log.LogFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 公钥证书模式的回调验签与多凭据轮换。
 *
 * <p><b>向量来源</b>：证书与密钥由 {@link PayTestCrypto} 在运行期生成（2048 位 RSA、自签、v3），
 * 报文签名由测试侧用 JDK {@code SHA256withRSA} 独立生成，与任何真实商户无关。</p>
 *
 * <p><b>凭据选取口径</b>：报文带 {@code alipay_cert_sn} 时只尝试
 * 序列号相等者；单证书配置可不带序列号；多证书且不带序列号无法确定归属，按失败处理。</p>
 */
public class PayVerifyCertTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 报文键：支付宝公钥证书序列号。
     */
    private static final String PARAM_CERT_SN = "alipay_cert_sn";

    /**
     * 换入前的日志工厂，用例结束后还原。
     */
    private LogFactory previousLogFactory;

    /**
     * 验签失败会记录一次 warn，需要把日志工厂换成空实现（框架要求应用装配日志工厂）。
     */
    @Before
    public void silenceLogs() {
        previousLogFactory = NoOpLogFactory.install();
    }

    /**
     * 清理门面持有器与日志工厂，避免用例间相互影响。
     */
    @After
    public void clearHolder() {
        PayTestEnv.clear();
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * 报文携带的证书序列号与配置的证书一致，且签名由该证书对应私钥生成 → 验签通过。
     */
    @Test
    public void shouldVerifyNotifyWhenCertSnMatchesConfiguredCert() {
        PayAppConfig app = install(PayTestProps.certMode(APP));
        String certSn = app.verifyKeys().get(0).certSn();
        assertTrue("证书序列号应为 32 位十六进制，实际为 " + certSn, certSn.matches("[0-9a-f]{32}"));

        Map<String, String> params = tradeNotify();
        params.put(PARAM_CERT_SN, certSn);
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);

        assertTrue("证书序列号匹配且签名正确时应通过", PayNotifyUtil.verify(APP, params));

        PayNotify result = PayNotifyUtil.verifyAndParse(APP, params);
        assertEquals(PayNotify.Type.TRADE, result.type());
        assertEquals("out_trade_no 应透出为幂等键", "T2026010100001", result.outTradeNo());
    }

    /**
     * 报文携带的证书序列号在配置中不存在 → 验签失败（不静默）。
     */
    @Test
    public void shouldRejectWhenCertSnMatchesNoConfiguredCert() {
        install(PayTestProps.certMode(APP));

        Map<String, String> params = tradeNotify();
        params.put(PARAM_CERT_SN, PayTestCrypto.UNKNOWN_CERT_SN);
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);

        assertFalse("序列号无匹配凭据时必须失败", PayNotifyUtil.verify(APP, params));
    }

    /**
     * 序列号匹配但签名并非该证书对应私钥所签 → 验签失败。
     */
    @Test
    public void shouldRejectWhenSignatureDoesNotMatchCertifiedKey() {
        PayAppConfig app = install(PayTestProps.certMode(APP));
        String certSn = app.verifyKeys().get(0).certSn();

        Map<String, String> params = tradeNotify();
        params.put(PARAM_CERT_SN, certSn);
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_NEW);

        assertFalse("签名与证书不匹配时必须失败", PayNotifyUtil.verify(APP, params));
    }

    /**
     * 只配置一张证书时，报文不带序列号也可按该证书验签通过。
     */
    @Test
    public void shouldVerifyWithSingleCertWhenMessageHasNoCertSn() {
        install(PayTestProps.certMode(APP));

        Map<String, String> params = tradeNotify();
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);

        assertTrue("单证书配置不应强制要求报文携带序列号", PayNotifyUtil.verify(APP, params));
    }

    /**
     * 配置两张证书而报文不带序列号时无法确定归属，按失败处理（不放行）。
     */
    @Test
    public void shouldRejectWhenMultipleCertsAndMessageHasNoCertSn() {
        install(rotationProps());

        Map<String, String> params = tradeNotify();
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);

        assertFalse("多证书且无序列号时不应猜测凭据", PayNotifyUtil.verify(APP, params));
    }

    /**
     * 同一 appId 下新旧两张证书并存，用旧证书签名与用新证书签名的报文均验签通过。
     */
    @Test
    public void shouldVerifyBothOldAndNewCertsDuringRotation() {
        PayAppConfig app = install(rotationProps());
        assertEquals("轮换期应同时持有两组凭据", 2, app.verifyKeys().size());
        String oldCertSn = app.verifyKeys().get(0).certSn();
        String newCertSn = app.verifyKeys().get(1).certSn();
        assertFalse("新旧证书序列号必须不同", oldCertSn.equalsIgnoreCase(newCertSn));

        Map<String, String> byOld = tradeNotify();
        byOld.put(PARAM_CERT_SN, oldCertSn);
        PayTestCrypto.signNotify(byOld, PayTestCrypto.KEY_PAIR_OLD);

        Map<String, String> byNew = tradeNotify();
        byNew.put(PARAM_CERT_SN, newCertSn);
        PayTestCrypto.signNotify(byNew, PayTestCrypto.KEY_PAIR_NEW);

        assertTrue("轮换期旧凭据应仍可通过", PayNotifyUtil.verify(APP, byOld));
        assertTrue("轮换期新凭据应可通过", PayNotifyUtil.verify(APP, byNew));
    }

    /**
     * 只配置新证书时，旧证书签名的报文必须验签失败（移除旧凭据即失效）。
     */
    @Test
    public void shouldRejectOldCertSignatureWhenOnlyNewCertConfigured() {
        String oldCertSn = PayConfig.parse(PayTestProps.certMode(APP)).apps().get(APP)
                .verifyKeys().get(0).certSn();
        PayTestEnv.install(PayTestProps.with(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "verify.0.value"), PayTestCrypto.CERT_NEW));

        Map<String, String> params = tradeNotify();
        params.put(PARAM_CERT_SN, oldCertSn);
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);

        assertFalse("旧凭据移除后旧签名必须失败", PayNotifyUtil.verify(APP, params));
    }

    /**
     * 证书模式下的验签失败经由 {@code verifyAndParse} 抛 {@link PayException}，且消息不含报文内容。
     */
    @Test
    public void shouldThrowWithoutLeakingMessageContentWhenCertSignatureInvalid() {
        install(PayTestProps.certMode(APP));

        Map<String, String> params = tradeNotify();
        PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);
        params.put("subject", "被篡改的标题");

        try {
            PayNotifyUtil.verifyAndParse(APP, params);
            fail("验签失败必须抛 PayException");
        } catch (PayException e) {
            assertTrue("消息应含 appId：" + e.getMessage(), e.getMessage().contains(APP));
            assertFalse("消息不得含报文内容：" + e.getMessage(), e.getMessage().contains("被篡改的标题"));
        }
    }

    /**
     * 装配证书模式的配置并返回主应用配置。
     *
     * @param properties 配置源
     * @return 主应用配置
     */
    private static PayAppConfig install(Properties properties) {
        PayConfig config = PayTestEnv.install(properties);
        return config.apps().get(APP);
    }

    /**
     * 轮换期配置：verify.0 为旧证书，verify.1 为新证书。
     *
     * @return 配置源
     */
    private static Properties rotationProps() {
        return PayTestProps.with(PayTestProps.certMode(APP),
                PayTestProps.key(APP, "verify.1.type"), "cert",
                PayTestProps.key(APP, "verify.1.value"), PayTestCrypto.CERT_NEW);
    }

    /**
     * 构造一笔交易类异步通知报文（未签名）。
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
        params.put("gmt_payment", "2026-11-26 10:00:00");
        return params;
    }
}
