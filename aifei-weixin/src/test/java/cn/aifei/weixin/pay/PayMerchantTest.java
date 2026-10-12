/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.support.PayTestKeys;
import java.security.KeyPair;
import java.security.PrivateKey;
import org.junit.Test;

/**
 * P0：商户凭据 fail-fast 与 PEM 解析
 */
public class PayMerchantTest {

    @Test
    public void ofPemParsesPkcs8Key() {
        KeyPair pair = PayTestKeys.generateKeyPair();
        PayMerchant merchant = PayMerchant.ofPem(PayTestKeys.MCH_ID, PayTestKeys.toPkcs8Pem(pair.getPrivate()),
                PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);

        assertEquals(PayTestKeys.MCH_ID, merchant.getMchId());
        assertEquals(PayTestKeys.MCH_SERIAL, merchant.getSerialNo());
        // 与原私钥可互换验证：同 message 签名可被同一公钥验证
        String signature = PaySignatureTest.signAndCapture(merchant.getPrivateKey(), "msg");
        assertTrue(PaySignatureTest.verifyWith(pair.getPublic(), "msg", signature));
    }

    @Test
    public void pemWithOrWithoutHeadersBothAccepted() {
        KeyPair pair = PayTestKeys.generateKeyPair();
        PrivateKey key = PayMerchant.parsePem(PayTestKeys.toPkcs8Pem(pair.getPrivate()));
        assertEquals(key, PayMerchant.parsePem(
                java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())));
    }

    @Test
    public void failFastOnBadArguments() {
        KeyPair pair = PayTestKeys.generateKeyPair();
        String pem = PayTestKeys.toPkcs8Pem(pair.getPrivate());
        try {
            PayMerchant.ofPem(" ", pem, PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        try {
            PayMerchant.ofPem(PayTestKeys.MCH_ID, null, PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        try {
            PayMerchant.ofPem(PayTestKeys.MCH_ID, pem, PayTestKeys.MCH_SERIAL, "short-key");
            fail();
        } catch (IllegalArgumentException ignore) {
        }
        try {
            PayMerchant.ofPem(PayTestKeys.MCH_ID, "not-base64!!", PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);
            fail();
        } catch (WxApiException ignore) {
        }
    }

    @Test
    public void toStringDoesNotLeakSecrets() {
        KeyPair pair = PayTestKeys.generateKeyPair();
        PayMerchant merchant = PayMerchant.ofPem(PayTestKeys.MCH_ID, PayTestKeys.toPkcs8Pem(pair.getPrivate()),
                PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);

        String text = merchant.toString();
        assertFalse(text.contains(PayTestKeys.API_V3_KEY));
        assertFalse(text.contains(java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())));
    }

    private static void assertTrue(boolean condition) {
        org.junit.Assert.assertTrue(condition);
    }
}
