/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.encrypt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

/**
 * 协议内核加密 / 签名向量（S1 验证标准 ②③④⑤）。
 *
 * <p>向量常量由首次运行固化，用于锁定实现不变；SHA-1 另用 NIST 标准向量（"abc"）独立校验。
 */
public class WxCryptTest {

    static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopq";
    static final String APP_ID = "wx1234567890abcdef";
    static final String TOKEN = "tokentokentokentoken";
    static final String PLAIN = "<xml><Content>微信 &amp; emoji 🎉 测试</Content></xml>";

    static final String CIPHER = "TbcpsiYUYjWyWs3BDngRY2c/S8sthXpJVnKLFNuH1DZUT76rauoZhaMMGDhjTDDG4mKsqrio1srjM/8deL3gs4LRBxT+dbFcjpKFCRpRakN6"
            + "maO3O9ZGAQd1E4bnTf9AnNTkBTwUAK/zauiXHEtNyg==";

    @Test
    public void sha1MatchesNistVectorAndSortsParts() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1Kit.sha1("abc"));
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Sha1Kit.sortedSign("c", "a", "b"));
    }

    @Test
    public void signatureVectorPlainAndEncrypted() {
        assertEquals("d1a5a45fe5fcb0beb9eb76f795d3bafecbc9c117", Sha1Kit.sortedSign(TOKEN, "1600000000", "1234567890"));
        assertEquals("1bd888b69e4e1296b33e64f49ef4e7cc248421f6",
                Sha1Kit.sortedSign(TOKEN, "1600000000", "1234567890", "CIPHERTEXT"));
    }

    @Test
    public void decryptFrozenVector() {
        assertEquals(PLAIN, WxCrypt.decrypt(AES_KEY, APP_ID, CIPHER));
    }

    @Test
    public void encryptDecryptRoundTripWithEdgeChars() {
        String[] plains = {"微信 & < > \" ' 测试", "emoji 🎉🎊", "]]>", "<xml></xml>", ""};
        for (String plain : plains) {
            assertEquals(plain, WxCrypt.decrypt(AES_KEY, APP_ID, WxCrypt.encrypt(AES_KEY, APP_ID, plain)));
        }
    }

    /**
     * 按官方算法在测试侧独立构造密文（固定 16 字节随机头），验证 WxCrypt 与之互通
     */
    @Test
    public void interopWithOfficialLayout() throws Exception {
        byte[] key = Base64.getDecoder().decode(AES_KEY + "=");
        byte[] plain = PLAIN.getBytes(StandardCharsets.UTF_8);
        byte[] appId = APP_ID.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        body.write((plain.length >> 24) & 0xFF);
        body.write((plain.length >> 16) & 0xFF);
        body.write((plain.length >> 8) & 0xFF);
        body.write(plain.length & 0xFF);
        body.write(plain, 0, plain.length);
        body.write(appId, 0, appId.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new IvParameterSpec(Arrays.copyOf(key, 16)));
        String cipherBase64 = Base64.getEncoder().encodeToString(cipher.doFinal(body.toByteArray()));

        assertEquals(PLAIN, WxCrypt.decrypt(AES_KEY, APP_ID, cipherBase64));
    }

    @Test
    public void appIdMismatchRejected() {
        try {
            WxCrypt.decrypt(AES_KEY, "wx-other-appid", CIPHER);
            fail("AppID 不一致必须抛异常");
        } catch (IllegalArgumentException expected) {
            // 预期：AppID 校验失败
        }
    }

    @Test
    public void aesKeyLengthValidated() {
        assertEquals(32, WxCrypt.decodeAesKey(AES_KEY).length);
        try {
            WxCrypt.decodeAesKey("too-short");
            fail("非 43 字符的 EncodingAESKey 必须抛异常");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }
}
