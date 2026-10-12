/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.support;

import cn.aifei.weixin.pay.PayMerchant;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 支付单测密钥工厂：RSA-2048 密钥对、PKCS#8 PEM、公钥 PEM、AES-GCM 加密助手
 */
public class PayTestKeys {

    public static final String MCH_ID = "1900000109";
    public static final String MCH_SERIAL = "5157F09EFDC096DE15EBE81A47057A72xxx";
    public static final String API_V3_KEY = "STiIfeCQbiKuy5ckHzFQfBMgEfYtWBXB";
    public static final String APP_ID = "wxminiapp123456";

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("RSA").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static PayMerchant merchant(PrivateKey privateKey) {
        return PayMerchant.of(MCH_ID, privateKey, MCH_SERIAL, API_V3_KEY);
    }

    public static String toPkcs8Pem(PrivateKey privateKey) {
        return pem("PRIVATE KEY", privateKey.getEncoded());
    }

    public static String toPublicKeyPem(PublicKey publicKey) {
        return pem("PUBLIC KEY", publicKey.getEncoded());
    }

    /** 官方回调样例同款参数布局：12 字符 ASCII nonce、associated_data、"transaction" 场景 */
    public static String encrypt(String apiV3Key, String nonce, String associatedData, String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            if (associatedData != null) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(
                    cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(String label, byte[] der) {
        String base64 = Base64.getEncoder().encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----";
    }

    private PayTestKeys() {
    }
}
