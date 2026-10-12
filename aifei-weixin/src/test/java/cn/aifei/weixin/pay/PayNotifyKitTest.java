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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.support.PayTestKeys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * P2：支付回调验签 + 解密（官方报文结构 + 测试向量自洽）
 */
public class PayNotifyKitTest {

    private static final String SERIAL = "PLAT-SERIAL-1";
    private static final String TIMESTAMP = "1759600000";
    private static final String NONCE = "nonce-nonce-nonce";

    private final KeyPair pair = PayTestKeys.generateKeyPair();
    private final PayMerchant merchant = PayMerchant.of(PayTestKeys.MCH_ID, pair.getPrivate(),
            PayTestKeys.MCH_SERIAL, PayTestKeys.API_V3_KEY);

    @Test
    public void verifyAcceptsValidSignature() {
        String body = "{\"id\":\"evt-1\",\"resource\":{\"ciphertext\":\"x\"}}";

        boolean ok = PayNotifyKit.verify(serial -> SERIAL.equals(serial) ? pair.getPublic() : null,
                headers(TIMESTAMP, NONCE, sign(TIMESTAMP, NONCE, body), SERIAL), body);

        assertTrue(ok);
    }

    @Test
    public void verifyFailsOnTamperedBodyOrUnknownSerial() {
        String body = "{\"id\":\"evt-1\"}";
        Map<String, String> headers = headers(TIMESTAMP, NONCE, sign(TIMESTAMP, NONCE, body), SERIAL);

        assertFalse(PayNotifyKit.verify(serial -> SERIAL.equals(serial) ? pair.getPublic() : null,
                headers, "{\"id\":\"evt-2\"}"));
        assertFalse("未知序列号应验签失败",
                PayNotifyKit.verify(serial -> null, headers, body));
        assertFalse("缺签名头应验签失败",
                PayNotifyKit.verify(serial -> pair.getPublic(), new HashMap<String, String>(), body));
    }

    @Test
    public void verifyIsCaseInsensitiveOnHeaderNames() {
        String body = "{\"id\":\"evt-1\"}";
        Map<String, String> headers = new HashMap<>();
        headers.put("wechatpay-timestamp", TIMESTAMP);
        headers.put("WECHATPAY-NONCE", NONCE);
        headers.put("Wechatpay-Signature", sign(TIMESTAMP, NONCE, body));
        headers.put("wechatpay-serial", SERIAL);

        assertTrue(PayNotifyKit.verify(serial -> SERIAL.equals(serial) ? pair.getPublic() : null, headers, body));
    }

    @Test
    public void decryptResourceReturnsPlaintextTransaction() {
        String transaction = "{\"out_trade_no\":\"ORDER-1\",\"transaction_id\":\"4200001\","
                + "\"trade_state\":\"SUCCESS\",\"amount\":{\"payer_total\":100,\"total\":100}}";
        String body = notifyBody(transaction);

        Map<String, Object> decrypted = PayNotifyKit.decryptResource(merchant, body);

        assertEquals("ORDER-1", decrypted.get("out_trade_no"));
        assertEquals("SUCCESS", decrypted.get("trade_state"));
    }

    @Test
    public void verifyAndDecryptThrowsOnBadSignature() {
        String body = notifyBody("{}");

        try {
            PayNotifyKit.verifyAndDecrypt(serial -> SERIAL.equals(serial) ? pair.getPublic() : null,
                    merchant, headers(TIMESTAMP, NONCE, "bad-signature", SERIAL), body);
            fail();
        } catch (WxApiException e) {
            assertTrue(e.getMessage().contains("验签失败"));
        }
    }

    @Test
    public void missingResourceThrows() {
        try {
            PayNotifyKit.decryptResource(merchant, "{\"id\":\"evt-1\"}");
            fail();
        } catch (WxApiException e) {
            assertTrue(e.getMessage().contains("resource"));
        }
    }

    private Map<String, String> headers(String timestamp, String nonce, String signature, String serial) {
        Map<String, String> headers = new HashMap<>();
        headers.put(PayNotifyKit.HEADER_TIMESTAMP, timestamp);
        headers.put(PayNotifyKit.HEADER_NONCE, nonce);
        headers.put(PayNotifyKit.HEADER_SIGNATURE, signature);
        headers.put(PayNotifyKit.HEADER_SERIAL, serial);
        return headers;
    }

    private String sign(String timestamp, String nonce, String body) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(pair.getPrivate());
            signature.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 官方通知报文结构：id/event_type/resource{ciphertext,nonce,associated_data,original_type} */
    private String notifyBody(String transactionPlain) {
        String ciphertext = PayTestKeys.encrypt(PayTestKeys.API_V3_KEY, "abcdef123456", "transaction",
                transactionPlain);
        return "{\"id\":\"evt-1\",\"create_time\":\"2026-10-05T10:00:00+08:00\","
                + "\"resource_type\":\"encrypt-resource\","
                + "\"event_type\":\"TRANSACTION.SUCCESS\",\"summary\":\"支付成功\","
                + "\"resource\":{\"original_type\":\"transaction\",\"algorithm\":\"AEAD_AES_256_GCM\","
                + "\"ciphertext\":\"" + ciphertext + "\",\"nonce\":\"abcdef123456\","
                + "\"associated_data\":\"transaction\"}}";
    }
}
