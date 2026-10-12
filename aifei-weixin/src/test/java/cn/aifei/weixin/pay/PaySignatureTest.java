/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.aifei.weixin.pay.core.PaySignature;
import cn.aifei.weixin.support.PayTestKeys;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * P0：请求签名 / Authorization 头格式 / 拉起支付二次签名
 */
public class PaySignatureTest {

    private static final Pattern SIGNATURE_PARAM =
            Pattern.compile("signature=\"([A-Za-z0-9+/=]+)\"");

    @Test
    public void signVerifiesWithPublicKey() {
        java.security.KeyPair pair = PayTestKeys.generateKeyPair();
        String signature = signAndCapture(pair.getPrivate(), "hello\nworld\n");

        assertTrue(verifyWith(pair.getPublic(), "hello\nworld\n", signature));
        assertFalse(verifyWith(pair.getPublic(), "tampered\n", signature));
    }

    @Test
    public void buildAuthorizationHeaderFormat() throws Exception {
        java.security.KeyPair pair = PayTestKeys.generateKeyPair();
        PayMerchant merchant = PayMerchant.of(PayTestKeys.MCH_ID, pair.getPrivate(),
                PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);

        String authorization = PaySignature.buildAuthorization(merchant, "POST",
                "/v3/pay/transactions/jsapi", "{\"appid\":\"a\"}");

        assertTrue(authorization.startsWith(PaySignature.AUTH_SCHEME + " mchid=\"" + PayTestKeys.MCH_ID + "\""));
        assertTrue(authorization.contains("serial_no=\"" + PayTestKeys.MCH_SERIAL + "\""));
        assertTrue(authorization.matches("(?s).*timestamp=\"\\d{10}\".*"));
        assertTrue(authorization.matches("(?s).*nonce_str=\"[A-Za-z0-9]{32}\".*"));

        // 从头中还原签名参数，按官方 message 结构互验
        long timestamp = Long.parseLong(extract(authorization, "timestamp"));
        String nonce = extract(authorization, "nonce_str");
        String signature = extract(authorization, "signature");
        String message = "POST\n/v3/pay/transactions/jsapi\n" + timestamp + "\n" + nonce + "\n{\"appid\":\"a\"}\n";
        assertTrue(verifyWith(pair.getPublic(), message, signature));
    }

    @Test
    public void buildJsapiPayParamsSignsOfficialMessage() {
        java.security.KeyPair pair = PayTestKeys.generateKeyPair();
        PayMerchant merchant = PayMerchant.of(PayTestKeys.MCH_ID, pair.getPrivate(),
                PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);

        PaySignature.JsapiPayParams params = PaySignature.buildJsapiPayParams(
                PayTestKeys.APP_ID, merchant, "wx201410272009395522657a690389285100");

        assertEquals(PayTestKeys.APP_ID, params.getAppId());
        assertEquals("prepay_id=wx201410272009395522657a690389285100", params.getPackageValue());
        assertNotNull(params.getPaySign());

        String message = params.getAppId() + "\n" + params.getTimeStamp() + "\n"
                + params.getNonceStr() + "\n" + params.getPackageValue() + "\n";
        assertTrue(verifyWith(pair.getPublic(), message, params.getPaySign()));
        assertTrue(params.toJson().contains("\"paySign\""));
    }

    static String signAndCapture(PrivateKey key, String message) {
        return PaySignature.sign(key, message);
    }

    static boolean verifyWith(PublicKey key, String message, String signatureBase64) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    private static void assertFalse(boolean condition) {
        org.junit.Assert.assertFalse(condition);
    }

    private static String extract(String authorization, String param) {
        Matcher matcher = Pattern.compile(param + "=\"([^\"]+)\"").matcher(authorization);
        assertTrue("缺少参数 " + param, matcher.find());
        return matcher.group(1);
    }
}
