/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * V3 应答 / 回调验签：message = timestamp\n nonce\n body\n，按 Wechatpay-Serial 取平台公钥验证。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/signature-verification.html
 */
public class PayVerifier {

    private PayVerifier() {
    }

    public static boolean verify(PlatformKeySource keys, String serialNo,
                                 String timestamp, String nonce, String body, String signatureBase64) {
        if (keys == null || serialNo == null || timestamp == null || nonce == null
                || body == null || signatureBase64 == null) {
            return false;
        }
        PublicKey key = keys.bySerial(serialNo);
        if (key == null) {
            return false;
        }
        String message = timestamp + "\n" + nonce + "\n" + body + "\n";
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }
}
