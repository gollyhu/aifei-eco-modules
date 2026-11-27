/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto;

import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * crypto 五个原语的功能与异常语义测试（设计 §11.3.1 / §11.10，测试策略 §3.7）。
 *
 * <p>向量一律用 JDK 独立参考实现与 RFC 2202 / RFC 4231 / RFC 6455 公开向量表达，
 * 不复制来源模块源码、协议字段或注释；全部用例零联网、可重复执行。</p>
 */
public class CryptoTest {

    /** RFC 6455 公开握手向量中的客户端 key。 */
    private static final String RFC6455_KEY = "dGhlIHNhbXBsZSBub25jZQ==";
    /** RFC 6455 公开向量中由调用方拼接的 GUID，仅存在于测试，不进入被测源码。 */
    private static final String RFC6455_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    /** RFC 6455 公开握手向量中的 accept 值。 */
    private static final String RFC6455_ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";
    /** RFC 2202 §3 TC2 / RFC 4231 §4.3 TC2 的 key。 */
    private static final String RFC_TC2_KEY = "Jefe";
    /** RFC 2202 §3 TC2 / RFC 4231 §4.3 TC2 的 data。 */
    private static final String RFC_TC2_DATA = "what do ya want for nothing?";
    /** RFC 2202 §3 TC2 HMAC-SHA1 十六进制摘要。 */
    private static final String RFC2202_TC2_SHA1 = "effcdf6ae5eb2fa2d27416d5f184df9c259a7c79";
    /** RFC 2202 §3 TC1 HMAC-SHA1 十六进制摘要（key 为 20 个 0x0b 字节）。 */
    private static final String RFC2202_TC1_SHA1 = "b617318655057264e28bc0b6fb378c8ef146be00";
    /** RFC 4231 §4.3 TC2 HMAC-SHA256 十六进制摘要。 */
    private static final String RFC4231_TC2_SHA256 =
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";
    /** RFC 4231 §4.2 TC1 HMAC-SHA256 十六进制摘要。 */
    private static final String RFC4231_TC1_SHA256 =
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7";

    @Test
    public void sha1Hex_knownVector() {
        String actual = CryptoUtil.sha1Hex("abc");
        assertEquals("SHA-1 标准向量不符", "a9993e364706816aba3e25717850c26c9cd0d89d", actual);
        assertEquals(40, actual.length());
        assertTrue("摘要须为 40 位小写十六进制", actual.matches("[0-9a-f]{40}"));
    }

    @Test
    public void sha1Hex_matchesJdkReference() throws Exception {
        String[] samples = {"", "abc", "hello world", "中文摘要", "a b\tc"};
        for (String text : samples) {
            assertEquals("text=" + text, jdkSha1Hex(text), CryptoUtil.sha1Hex(text));
        }
    }

    @Test
    public void sha1Base64_matchesJdkReference() throws Exception {
        String[] samples = {"", "abc", "hello world", "中文摘要"};
        for (String text : samples) {
            assertEquals("text=" + text,
                    Base64.getEncoder().encodeToString(sha1Bytes(text)),
                    CryptoUtil.sha1Base64(text));
        }
    }

    @Test
    public void sha1Base64_rfc6455Vector() throws Exception {
        assertEquals("RFC 6455 公开 accept 向量不符", RFC6455_ACCEPT,
                CryptoUtil.sha1Base64(RFC6455_KEY + RFC6455_GUID));
        assertEquals("JDK 独立参考不符", RFC6455_ACCEPT,
                Base64.getEncoder().encodeToString(sha1Bytes(RFC6455_KEY + RFC6455_GUID)));
    }

    @Test
    public void hmacSha1_rfc2202AndJdkReference() throws Exception {
        assertEquals("RFC 2202 TC2 HMAC-SHA1 不符",
                base64OfHex(RFC2202_TC2_SHA1), CryptoUtil.hmacSha1(RFC_TC2_KEY, RFC_TC2_DATA));
        assertEquals("RFC 2202 TC1 HMAC-SHA1 不符",
                base64OfHex(RFC2202_TC1_SHA1),
                CryptoUtil.hmacSha1(repeatedKey(0x0b, 20), "Hi There"));
        String[] keys = {"key", "中文密钥", "a longer key for mac"};
        String[] datas = {"", "data", "中文消息", RFC_TC2_DATA};
        for (int i = 0; i < keys.length; i++) {
            assertEquals("JDK 独立参考 HMAC-SHA1 不符: " + i,
                    jdkHmacBase64("HmacSHA1", keys[i], datas[i]),
                    CryptoUtil.hmacSha1(keys[i], datas[i]));
        }
    }

    @Test
    public void hmacSha256_rfc4231AndJdkReference() throws Exception {
        assertEquals("RFC 4231 TC2 HMAC-SHA256 不符",
                base64OfHex(RFC4231_TC2_SHA256), CryptoUtil.hmacSha256(RFC_TC2_KEY, RFC_TC2_DATA));
        assertEquals("RFC 4231 TC1 HMAC-SHA256 不符",
                base64OfHex(RFC4231_TC1_SHA256),
                CryptoUtil.hmacSha256(repeatedKey(0x0b, 20), "Hi There"));
        String[] keys = {"key", "中文密钥", "a longer key for mac"};
        String[] datas = {"", "data", "中文消息", RFC_TC2_DATA};
        for (int i = 0; i < keys.length; i++) {
            assertEquals("JDK 独立参考 HMAC-SHA256 不符: " + i,
                    jdkHmacBase64("HmacSHA256", keys[i], datas[i]),
                    CryptoUtil.hmacSha256(keys[i], datas[i]));
        }
    }

    @Test
    public void hmac_keyDataOrder() throws Exception {
        assertFalse("HMAC(K,text) 与 HMAC(text,K) 不应相同",
                CryptoUtil.hmacSha1("key", "data").equals(CryptoUtil.hmacSha1("data", "key")));
        assertFalse("HMAC-SHA256 同样须区分 key/data 顺序",
                CryptoUtil.hmacSha256("key", "data").equals(CryptoUtil.hmacSha256("data", "key")));
        assertEquals("空消息 HMAC 应与 JDK 参考一致",
                jdkHmacBase64("HmacSHA1", "key", ""), CryptoUtil.hmacSha1("key", ""));
    }

    @Test
    public void constantTimeEquals_trueAndFalse() {
        assertTrue("相等串", CryptoUtil.constantTimeEquals("same", "same"));
        assertTrue("两个空串视为相等", CryptoUtil.constantTimeEquals("", ""));
        assertTrue("UTF-8 中文相等", CryptoUtil.constantTimeEquals("中文", "中文"));
        assertFalse("不同串", CryptoUtil.constantTimeEquals("abc", "abd"));
        assertFalse("长度不同", CryptoUtil.constantTimeEquals("abc", "abcd"));
    }

    @Test
    public void constantTimeEquals_nullReturnsFalse() {
        assertFalse(CryptoUtil.constantTimeEquals(null, "x"));
        assertFalse(CryptoUtil.constantTimeEquals("x", null));
        assertFalse(CryptoUtil.constantTimeEquals(null, null));
        assertFalse(CryptoUtil.constantTimeEquals(null, ""));
        assertFalse(CryptoUtil.constantTimeEquals("", null));
    }

    @Test
    public void nullAndInvalidParams_readableException() {
        assertCryptoException("crypto: text must not be null", new Runnable() {
            public void run() {
                CryptoUtil.sha1Hex(null);
            }
        });
        assertCryptoException("crypto: text must not be null", new Runnable() {
            public void run() {
                CryptoUtil.sha1Base64(null);
            }
        });
        assertCryptoException("crypto: key must not be null", new Runnable() {
            public void run() {
                CryptoUtil.hmacSha1(null, "x");
            }
        });
        assertCryptoException("crypto: key must not be null", new Runnable() {
            public void run() {
                CryptoUtil.hmacSha256(null, "x");
            }
        });
        assertCryptoException("crypto: data must not be null", new Runnable() {
            public void run() {
                CryptoUtil.hmacSha1("k", null);
            }
        });
        assertCryptoException("crypto: data must not be null", new Runnable() {
            public void run() {
                CryptoUtil.hmacSha256("k", null);
            }
        });

        // 算法不可用 / hmac 失败路径在标准 JDK 上不可直接构造，反射校验错误工厂的 cause 保留语义
        Throwable cause = new IllegalStateException("boom");
        try {
            Class<?> errors = Class.forName("cn.hg.aifei.tools.crypto.internal.CryptoErrors");
            Method hmacFailed = errors.getMethod("hmacFailed", Throwable.class);
            CryptoException first = (CryptoException) hmacFailed.invoke(null, cause);
            assertTrue(first.getMessage().startsWith("crypto: hmac failed, cause: "));
            assertSame("hmac 失败须保留 cause", cause, first.getCause());
            Method notAvailable = errors.getMethod("algorithmNotAvailable", String.class, Throwable.class);
            CryptoException second = (CryptoException) notAvailable.invoke(null, "HmacSHA1", cause);
            assertTrue(second.getMessage().startsWith(
                    "crypto: algorithm \"HmacSHA1\" not available in this JDK, cause: "));
            assertSame("算法不可用须保留 cause", cause, second.getCause());
        } catch (Exception e) {
            throw new AssertionError("反射校验内部错误工厂失败", e);
        }
    }

    // ---------------------------------------------------------------- 工具

    private static void assertCryptoException(String expected, Runnable action) {
        try {
            action.run();
            fail("应抛 CryptoException: " + expected);
        } catch (CryptoException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private static byte[] sha1Bytes(String text) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String jdkSha1Hex(String text) throws Exception {
        return hex(sha1Bytes(text));
    }

    private static String jdkHmacBase64(String alg, String key, String data) throws Exception {
        Mac mac = Mac.getInstance(alg);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), alg));
        return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private static String base64OfHex(String hexText) {
        return Base64.getEncoder().encodeToString(hexBytes(hexText));
    }

    private static byte[] hexBytes(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0x0F, 16));
            sb.append(Character.forDigit(b & 0x0F, 16));
        }
        return sb.toString();
    }

    private static String repeatedKey(int value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, (char) value);
        return new String(chars);
    }
}
