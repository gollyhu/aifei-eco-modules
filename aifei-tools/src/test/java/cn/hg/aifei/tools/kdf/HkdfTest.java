/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * HKDF 对标 RFC 5869 附录 A 测试向量。
 *
 * <p>SHA-256 档照抄 RFC 5869 A.1 Case 1 / Case 2 的输入与 OKM，逐字比对输出字节；
 * SHA-512 档输入同 Case 1 / Case 2，OKM 为按 RFC 5869 算法用 HmacSHA512 独立计算的
 * 参考值（RFC 5869 附录 A 未提供 SHA-512 向量，F9-b：硬编码常量、不联网）。</p>
 */
public class HkdfTest {

    @Test
    public void rfc5869_case1_sha256() {
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = new byte[13];
        for (int i = 0; i < 13; i++) {
            salt[i] = (byte) i;
        }
        byte[] info = new byte[10];
        for (int i = 0; i < 10; i++) {
            info[i] = (byte) (0xf0 + i);
        }
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA256, salt, info, 42);
        byte[] okm = KdfKit.derive(spec, ikm);
        // RFC 5869 A.1 Case 1 OKM
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
                okm);
    }

    @Test
    public void rfc5869_case2_sha256_saltAndInfoEmpty() {
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA256, null, null, 42);
        byte[] okm = KdfKit.derive(spec, ikm);
        // RFC 5869 A.1 Case 2 OKM（salt 与 info 均为零长度）
        assertArrayEquals(hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
                okm);
    }

    @Test
    public void rfc5869_case1_inputWithSha512() {
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = new byte[13];
        for (int i = 0; i < 13; i++) {
            salt[i] = (byte) i;
        }
        byte[] info = new byte[10];
        for (int i = 0; i < 10; i++) {
            info[i] = (byte) (0xf0 + i);
        }
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA512, salt, info, 42);
        byte[] okm = KdfKit.derive(spec, ikm);
        // SHA-512 参考向量（输入同 RFC 5869 A.1 Case 1，输出按 RFC 5869 算法用 HmacSHA512 计算）
        assertArrayEquals(hex("832390086cda71fb47625bb5ceb168e4c8e26a1a16ed34d9fc7fe92c1481579338da362cb8d9f925d7cb"),
                okm);
    }

    @Test
    public void rfc5869_case2_inputWithSha512_saltAndInfoEmpty() {
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA512, null, null, 42);
        byte[] okm = KdfKit.derive(spec, ikm);
        // SHA-512 参考向量（输入同 RFC 5869 A.1 Case 2，输出按 RFC 5869 算法用 HmacSHA512 计算）
        assertArrayEquals(hex("f5fa02b18298a72a8c23898a8703472c6eb179dc204c03425c970e3b164bf90fff22d04836d0e2343bac"),
                okm);
    }

    @Test
    public void hkdf_exceedsRfc5869Max_throws() {
        // SHA-256 HashLen=32，上限 255*32=8160；超过应抛
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA256, null, null, 8161);
        try {
            KdfKit.derive(spec, new byte[]{1});
            fail("超过 RFC5869 上限应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: keyLen 8161 exceeds RFC5869 maximum 8160 for SHA256", e.getMessage());
        }
    }

    @Test
    public void hkdf_keyLenEqualsHashLen() {
        // 输出长度恰为 HashLen 的倍数边界（counter=1 即足）
        byte[] ikm = new byte[]{1, 2, 3};
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA256, new byte[]{0}, new byte[]{1}, 32);
        byte[] okm = KdfKit.derive(spec, ikm);
        assertEquals(32, okm.length);
    }

    /** hex 字符串解码为字节数组。 */
    private static byte[] hex(String s) {
        int n = s.length();
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return out;
    }
}
