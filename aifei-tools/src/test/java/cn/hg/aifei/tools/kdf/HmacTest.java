/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * HMAC 派生原语对标 RFC 4231 测试向量（设计 §3.5 / §8-F10）。
 *
 * <p>{@link HmacEngine} 对齐 RFC 2104 {@code HMAC(K, text)}：secret=ikm（K）、data=message（text）。
 * 两个 derive 重载：两参 {@code derive(spec, ikm)} 等价 data=null（HMAC 视空字节 {@code HMAC(ikm, "")}）；
 * 三参 {@code derive(spec, ikm, data)} 的 data 是 HMAC 的 message。keyLen 语义：keyLen≤HashLen
 * 截取前 keyLen 字节、keyLen&gt;HashLen 按 RFC 5869 expand 风格循环扩展（HMAC 作 PRF）。</p>
 *
 * <p>对标方式：① 三参 {@code derive(spec, ikm, data)} 的 data=message 路径，用 RFC 4231 TC2/TC3/TC6/TC7
 * 的 secret 当 ikm、message 当 data，逐字比对输出字节（SHA256/SHA512 两档，F9-b 硬编码常量）；
 * ② 两参便捷重载（data=null 视空字节 {@code HMAC(ikm, "")}）；③ keyLen≤HashLen 截取 + &gt;HashLen expand 扩展。</p>
 */
public class HmacTest {

    @Test
    public void rfc4231_case2_threeArg_sha256() {
        // RFC 4231 §4.3 TC2：key="Jefe", data="what do ya want for nothing?"，HMAC-SHA256
        byte[] ikm = "Jefe".getBytes();
        byte[] data = "what do ya want for nothing?".getBytes();
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 32);
        byte[] out = KdfKit.derive(spec, ikm, data);
        assertArrayEquals(hex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"), out);
    }

    @Test
    public void rfc4231_case3_threeArg_sha256() {
        // RFC 4231 §4.4 TC3：key=0xaa*20, data=0xdd*50，HMAC-SHA256
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0xaa);
        byte[] data = new byte[50];
        java.util.Arrays.fill(data, (byte) 0xdd);
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 32);
        byte[] out = KdfKit.derive(spec, ikm, data);
        assertArrayEquals(hex("773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe"), out);
    }

    @Test
    public void rfc4231_case6_threeArg_sha512() {
        // RFC 4231 §4.10 TC6：key=0x0b*20, data="Hi There"，HMAC-SHA512
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] data = "Hi There".getBytes();
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA512, 64);
        byte[] out = KdfKit.derive(spec, ikm, data);
        assertArrayEquals(hex("87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde"
                + "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854"), out);
    }

    @Test
    public void rfc4231_case7_threeArg_sha512() {
        // RFC 4231 §4.11 TC7：key=0xaa*20, data=0xdd*50，HMAC-SHA512
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0xaa);
        byte[] data = new byte[50];
        java.util.Arrays.fill(data, (byte) 0xdd);
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA512, 64);
        byte[] out = KdfKit.derive(spec, ikm, data);
        assertArrayEquals(hex("fa73b0089d56a284efb0f0756c890be9b1b5dbdd8ee81a3655f83e33b2279d39"
                + "bf3e848279a722c806b485a47e67c807b946a337bee8942674278859e13292fb"), out);
    }

    @Test
    public void twoArg_sha256_dataNullEqualsEmptyBytes() {
        // 两参重载等价 data=null，HMAC(ikm, "")
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 32);
        byte[] out = KdfKit.derive(spec, ikm);
        assertArrayEquals(hex("999a901219f032cd497cadb5e6051e97b6a29ab297bd6ae722bd6062a2f59542"), out);
        assertEquals(32, out.length);
    }

    @Test
    public void twoArg_sha512_dataNullEqualsEmptyBytes() {
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA512, 64);
        byte[] out = KdfKit.derive(spec, ikm);
        assertArrayEquals(hex("ad8da3d882af6e9b872457adcdd638e9b87af44825425085f8ce81a4122bab78"
                + "1b92f5ab92ac24948ad369f86558fd469ca3f4861cb0f0dfb33154428ed03dfb"), out);
        assertEquals(64, out.length);
    }

    @Test
    public void twoArg_dataNullEqualsThreeArgDataNull() {
        // 两参 derive(spec, ikm) 与三参 derive(spec, ikm, null) 输出一致
        byte[] ikm = new byte[]{1, 2, 3, 4};
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 32);
        assertArrayEquals(KdfKit.derive(spec, ikm), KdfKit.derive(spec, ikm, null));
    }

    @Test
    public void hmac_keyLenTruncateLessThanHashLen() {
        // keyLen=16 < SHA256 HashLen=32，截取前 16 字节
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 16);
        byte[] out = KdfKit.derive(spec, ikm);
        assertEquals(16, out.length);
        // 截取前 16 字节 = HMAC(ikm,"") 的前 16 字节
        assertArrayEquals(hex("999a901219f032cd497cadb5e6051e97"), out);
    }

    @Test
    public void hmac_keyLenExpandGreaterThanHashLen() {
        // keyLen=50 > SHA256 HashLen=32，按 RFC5869 expand 风格循环扩展
        byte[] ikm = new byte[20];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 50);
        byte[] out = KdfKit.derive(spec, ikm);
        assertEquals(50, out.length);
        // 前 32 字节 = HMAC(ikm,"") 原始输出，后 18 字节 = expand T(1) 前 18 字节
        assertArrayEquals(hex("999a901219f032cd497cadb5e6051e97b6a29ab297bd6ae722bd6062a2f59542"
                + "54c419dfb0d8026660da154a00b4c73c9820"), out);
    }

    @Test
    public void hmac_dataIgnoredForHkdfAndPbkdf2() {
        // HKDF/PBKDF2 的 data 入参被忽略：三参与两参输出一致
        byte[] ikm = new byte[]{1, 2, 3};
        KdfSpec hkdf = KdfSpec.hkdf(KdfHash.SHA256, new byte[]{0}, new byte[]{1}, 32);
        assertArrayEquals(KdfKit.derive(hkdf, ikm), KdfKit.derive(hkdf, ikm, new byte[]{9, 9}));
        KdfSpec pbkdf2 = KdfSpec.pbkdf2(KdfHash.SHA256, new byte[]{0}, 100, 32);
        assertArrayEquals(KdfKit.derive(pbkdf2, ikm), KdfKit.derive(pbkdf2, ikm, new byte[]{9, 9}));
    }

    private static byte[] hex(String s) {
        int n = s.length();
        byte[] out = new byte[n / 2];
        for (int i = 0; i < n; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(s.substring(i, i + 2), 16);
        }
        return out;
    }
}
