/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto.internal;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC 消息认证码计算的唯一实现点，按 RFC 2104 的 {@code HMAC(K, text)} 顺序传参。
 */
public final class HmacEngine {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacEngine() {
    }

    /**
     * 计算 HMAC-SHA1 并转为标准 Base64。
     *
     * @param key 密钥文本，不得为 null
     * @param data 消息文本，不得为 null
     * @return 标准 Base64 编码的认证码
     */
    public static String hmacSha1(String key, String data) {
        return hmac(HMAC_SHA1, key, data);
    }

    /**
     * 计算 HMAC-SHA256 并转为标准 Base64。
     *
     * @param key 密钥文本，不得为 null
     * @param data 消息文本，不得为 null
     * @return 标准 Base64 编码的认证码
     */
    public static String hmacSha256(String key, String data) {
        return hmac(HMAC_SHA256, key, data);
    }

    private static String hmac(String alg, String key, String data) {
        if (key == null) {
            throw CryptoErrors.nullKey();
        }
        if (data == null) {
            throw CryptoErrors.nullData();
        }
        try {
            Mac mac = Mac.getInstance(alg);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), alg));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (NoSuchAlgorithmException e) {
            throw CryptoErrors.algorithmNotAvailable(alg, e);
        } catch (InvalidKeyException e) {
            throw CryptoErrors.hmacFailed(e);
        }
    }
}
