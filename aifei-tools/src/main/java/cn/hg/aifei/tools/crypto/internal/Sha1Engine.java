/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * SHA-1 摘要计算的唯一实现点，输出十六进制与标准 Base64 两种形态。
 */
public final class Sha1Engine {

    private Sha1Engine() {
    }

    /**
     * 计算摘要并逐字节转为 40 位小写十六进制。
     *
     * @param text 待摘要文本，不得为 null
     * @return 40 位小写十六进制摘要
     */
    public static String sha1Hex(String text) {
        byte[] digest = digest(text);
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(Character.forDigit((b >> 4) & 0x0F, 16));
            out.append(Character.forDigit(b & 0x0F, 16));
        }
        return out.toString();
    }

    /**
     * 计算摘要并转为标准 Base64 字符串。
     *
     * @param text 待摘要文本，不得为 null
     * @return 标准 Base64 编码的摘要
     */
    public static String sha1Base64(String text) {
        return Base64.getEncoder().encodeToString(digest(text));
    }

    private static byte[] digest(String text) {
        if (text == null) {
            throw CryptoErrors.nullText();
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            return messageDigest.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw CryptoErrors.algorithmNotAvailable("SHA-1", e);
        }
    }
}
