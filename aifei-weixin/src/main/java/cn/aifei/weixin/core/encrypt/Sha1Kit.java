/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.encrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * SHA-1 摘要工具（零依赖，Java 内置 MessageDigest）。
 */
public class Sha1Kit {

    /**
     * 微信签名：参数按字典序排序后直接拼接，取 40 位小写十六进制。
     *
     * <p>明文模式：{@code sortedSign(token, timestamp, nonce)}；
     * 安全模式：{@code sortedSign(token, timestamp, nonce, encrypt)}。
     */
    public static String sortedSign(String... parts) {
        String[] sorted = Arrays.copyOf(parts, parts.length);
        Arrays.sort(sorted);

        StringBuilder sb = new StringBuilder(64);
        for (String part : sorted) {
            sb.append(part);
        }
        return sha1(sb.toString());
    }

    /**
     * 直接对文本取 SHA-1，40 位小写十六进制（不做排序，用于 JS-SDK 前端签名等定序拼接场景）
     */
    public static String sha1(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder ret = new StringBuilder(40);
            for (byte b : bytes) {
                ret.append(Character.forDigit((b >> 4) & 0x0F, 16));
                ret.append(Character.forDigit(b & 0x0F, 16));
            }
            return ret.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-1", e);
        }
    }

    private Sha1Kit() {
    }
}
