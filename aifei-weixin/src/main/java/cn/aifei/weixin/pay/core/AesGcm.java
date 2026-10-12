/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay.core;

import cn.aifei.weixin.common.WxApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * APIv3 报文解密：AES-256-GCM，密钥为 APIv3 密钥，tag 128 位。
 *
 * <p>官方文档：https://pay.weixin.qq.com/docs/merchant/development/interface-rules/certificate-callback-decryption.html
 */
public class AesGcm {

    private static final int TAG_LENGTH_BITS = 128;

    private AesGcm() {
    }

    /**
     * 解密回调 resource 或平台证书 encrypt_certificate 的 ciphertext
     *
     * @param associatedData 可为 null（官方回调解密常为 "transaction" / "refund" 等）
     */
    public static String decrypt(String apiV3Key, String nonce, String ciphertextBase64, String associatedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WxApiException("APIv3 报文解密失败（密钥 / nonce / associated_data 不匹配或密文被篡改）", e);
        }
    }
}
