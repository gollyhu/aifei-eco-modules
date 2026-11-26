/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal.sms4j;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 阿里云短信状态报告回调验签唯一实现（设计 §8.3）：HMAC-SHA1，纯 JDK，常量时间比较。
 *
 * <p>sms4j 只覆盖出站发送、不提供入站回执验签，故本类是职责边界补充，不替换发送内核。</p>
 */
public final class AliyunReceiptVerifier {

    private static final String SIGNATURE_KEY = "signature";
    private static final String SIGNATURE_KEY_ALT = "Signature";
    private static final String ALGORITHM = "HmacSHA1";

    /**
     * 校验回调签名。
     *
     * @param params          回调参数（含 signature 字段）
     * @param accessKeySecret 回执验签密钥（来自通道配置）
     * @return 验签是否通过；参数 / 密钥 / 签名缺失或算法异常均返回 {@code false}
     */
    public boolean verify(Map<String, String> params, String accessKeySecret) {
        if (params == null || accessKeySecret == null || accessKeySecret.trim().isEmpty()) {
            return false;
        }
        String provided = params.get(SIGNATURE_KEY);
        if (provided == null) {
            provided = params.get(SIGNATURE_KEY_ALT);
        }
        if (provided == null || provided.trim().isEmpty()) {
            return false;
        }
        try {
            List<String> keys = new ArrayList<String>(params.keySet());
            keys.remove(SIGNATURE_KEY);
            keys.remove(SIGNATURE_KEY_ALT);
            Collections.sort(keys);
            StringBuilder canonical = new StringBuilder();
            for (String key : keys) {
                String value = params.get(key);
                canonical.append(key).append(value == null ? "" : value);
            }
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] signature = mac.doFinal(canonical.toString().getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(signature);
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    provided.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
