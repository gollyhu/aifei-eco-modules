/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.encrypt;

import cn.aifei.weixin.WxConsts;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 微信消息加解密（AES-256-CBC）。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Message_Encryption_and_Decryption.html
 *
 * <pre>
 * 要点：
 *  1: AESKey = Base64(EncodingAESKey + "=")，43 字符解码后为 32 字节；IV 取前 16 字节
 *  2: 明文结构 = 16 字节随机串 + 4 字节大端消息长度 + 消息体 + appId
 *  3: 填充使用 Java 内置 PKCS5Padding（16 字节块下与 PKCS#7 等价），加解密均自动处理，
 *     禁止手工再补位 / 再去填充（手工去填充会破坏尾部为 0x01 的中文消息）
 * </pre>
 */
public class WxCrypt {

    /**
     * EncodingAESKey（43 字符）解码为 32 字节 AES 密钥
     */
    public static byte[] decodeAesKey(String encodingAESKey) {
        if (encodingAESKey == null || encodingAESKey.length() != WxConsts.ENCODING_AES_KEY_LENGTH) {
            throw new IllegalArgumentException("EncodingAESKey 必须为 43 字符");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodingAESKey + "=");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("EncodingAESKey 不是合法 Base64", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("EncodingAESKey 解码后必须为 32 字节，实际 " + keyBytes.length);
        }
        return keyBytes;
    }

    /**
     * 加密：16 字节随机串 + 4 字节大端长度 + 明文 + appId，再 AES-256-CBC 加密并 Base64 编码
     */
    public static String encrypt(String encodingAESKey, String appId, String plain) {
        byte[] keyBytes = decodeAesKey(encodingAESKey);
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        byte[] appIdBytes = appId.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream body = new ByteArrayOutputStream(plainBytes.length + appIdBytes.length + 32);
        byte[] randomBytes = RandomKit.randomStr().getBytes(StandardCharsets.UTF_8);
        body.write(randomBytes, 0, randomBytes.length);
        body.write((plainBytes.length >> 24) & 0xFF);
        body.write((plainBytes.length >> 16) & 0xFF);
        body.write((plainBytes.length >> 8) & 0xFF);
        body.write(plainBytes.length & 0xFF);
        body.write(plainBytes, 0, plainBytes.length);
        body.write(appIdBytes, 0, appIdBytes.length);

        byte[] encrypted = doFinal(Cipher.ENCRYPT_MODE, keyBytes, body.toByteArray());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 解密：Base64 解码 → AES 解密（自动去填充）→ 校验 appId → 截取消息体
     */
    public static String decrypt(String encodingAESKey, String appId, String encryptBase64) {
        byte[] keyBytes = decodeAesKey(encodingAESKey);

        byte[] cipherBytes;
        try {
            cipherBytes = Base64.getDecoder().decode(encryptBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("密文不是合法 Base64", e);
        }

        byte[] plain = doFinal(Cipher.DECRYPT_MODE, keyBytes, cipherBytes);
        if (plain.length < 20) {
            throw new IllegalArgumentException("解密结果长度不足：" + plain.length);
        }

        int msgLength = ((plain[16] & 0xFF) << 24)
                | ((plain[17] & 0xFF) << 16)
                | ((plain[18] & 0xFF) << 8)
                | (plain[19] & 0xFF);
        if (msgLength < 0 || 20 + msgLength > plain.length) {
            throw new IllegalArgumentException("解密结果消息长度非法：" + msgLength);
        }

        String fromAppId = new String(plain, 20 + msgLength, plain.length - 20 - msgLength, StandardCharsets.UTF_8);
        if (!appId.equals(fromAppId)) {
            throw new IllegalArgumentException("AppID 校验失败：期望 " + appId + "，实际 " + fromAppId);
        }

        return new String(plain, 20, msgLength, StandardCharsets.UTF_8);
    }

    private static byte[] doFinal(int mode, byte[] keyBytes, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(Arrays.copyOf(keyBytes, 16));
            cipher.init(mode, keySpec, ivSpec);
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("AES 加解密失败", e);
        }
    }

    private WxCrypt() {
    }
}
