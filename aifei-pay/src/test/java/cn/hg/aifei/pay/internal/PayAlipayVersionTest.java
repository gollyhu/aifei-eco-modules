/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.support.PayTestCrypto;
import cn.hg.aifei.pay.support.PayTestProps;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * 支付宝版本矩阵：alipay2 / alipay3 类型白名单、V2 客户端与验签链路。
 */
public class PayAlipayVersionTest {

    private static final String APP = PayTestProps.APP_MAIN;

    @Test
    public void shouldExposeVersionedTypesOnly() {
        assertEquals("alipay2", PayTypes.requireSupported("pay.main.type", "alipay2"));
        assertEquals("alipay3", PayTypes.requireSupported("pay.main.type", "alipay3"));
        assertTrue("应支持 V2", PayTypes.isSupported("alipay2"));
        assertTrue("应支持 V3", PayTypes.isSupported("alipay3"));
        assertTrue("无版本含义的 alipay 必须被拒绝", !PayTypes.isSupported("alipay"));
    }

    @Test
    public void shouldBuildV2Client() {
        PayConfig config = PayConfig.parse(PayTestProps.keyMode(APP));
        AliPayV2Clients clients = new AliPayV2Clients(config);
        clients.preload();

        assertNotNull("V2 客户端应可构建", clients.client(APP));
    }

    @Test
    public void shouldVerifyWithV2Signature() {
        PayConfig config = PayConfig.parse(PayTestProps.keyMode(APP));
        Map<String, String> params = signedParams();
        AliPayV2Verifier verifier = new AliPayV2Verifier(config.apps());

        assertTrue("V2 验签应通过", verifier.verify(APP, params));

        params.put("total_amount", "9.99");
        assertTrue("篡改报文应验签失败", !verifier.verify(APP, params));
    }

    @Test
    public void shouldProbeBothKernelVersions() {
        PayDeps.requireV2Kernel();
        PayDeps.requireV3Kernel();
    }

    @Test
    public void shouldRejectLegacyAlipayType() {
        PayException e = assertThrows(PayException.class,
                () -> PayTypes.requireSupported("pay.main.type", "alipay"));
        assertTrue("消息应列出合法类型", e.getMessage().contains("合法类型"));
    }

    private static Map<String, String> signedParams() {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("out_trade_no", "T2026010100001");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("total_amount", "0.01");
        return PayTestCrypto.signNotify(params, PayTestCrypto.KEY_PAIR_OLD);
    }
}
