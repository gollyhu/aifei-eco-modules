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
 * PBKDF2 对标 RFC 7914 §B.1 / RFC 8018 测试向量。
 *
 * <p>SHA-256 档照抄 RFC 7914 §B.1 两组官方向量（passwd/salt/1/64、Password/NaCl/80000/64），
 * 逐字比对输出字节；SHA-512 档输入同 RFC 7914 §B.1 第一组，输出按 RFC 8018 §5.2 算法用
 * HmacSHA512 独立计算的参考值（RFC 7914 §B.1 仅提供 SHA-256 向量，硬编码常量、不联网）。</p>
 */
public class Pbkdf2Test {

    @Test
    public void rfc7914_case1_sha256() {
        KdfSpec spec = KdfSpec.pbkdf2(KdfHash.SHA256, "salt".getBytes(), 1, 64);
        byte[] dk = KdfKit.derive(spec, "passwd".getBytes());
        assertArrayEquals(hex("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
                + "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"), dk);
    }

    @Test
    public void rfc7914_case2_sha256() {
        KdfSpec spec = KdfSpec.pbkdf2(KdfHash.SHA256, "NaCl".getBytes(), 80000, 64);
        byte[] dk = KdfKit.derive(spec, "Password".getBytes());
        assertArrayEquals(hex("4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56"
                + "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"), dk);
    }

    @Test
    public void rfc7914_case1_inputWithSha512() {
        KdfSpec spec = KdfSpec.pbkdf2(KdfHash.SHA512, "salt".getBytes(), 1, 64);
        byte[] dk = KdfKit.derive(spec, "passwd".getBytes());
        assertArrayEquals(hex("c74319d99499fc3e9013acff597c23c5baf0a0bec5634c46b8352b793e324723"
                + "d55caa76b2b25c43402dcfdc06cdcf66f95b7d0429420b39520006749c51a04e"), dk);
    }

    @Test
    public void pbkdf2_arbitraryKeyLen() {
        KdfSpec spec = KdfSpec.pbkdf2(KdfHash.SHA256, "salt".getBytes(), 100, 50);
        byte[] dk = KdfKit.derive(spec, "password".getBytes());
        assertEquals(50, dk.length);
        assertEquals(64, KdfKit.derive(
                KdfSpec.pbkdf2(KdfHash.SHA256, "salt".getBytes(), 100, 64),
                "password".getBytes()).length);
        assertEquals(100, KdfKit.derive(
                KdfSpec.pbkdf2(KdfHash.SHA512, "salt".getBytes(), 100, 100),
                "password".getBytes()).length);
    }

    @Test
    public void pbkdf2_iterationsAffectOutput() {
        byte[] salt = "salt".getBytes();
        byte[] pw = "password".getBytes();
        byte[] dk1 = KdfKit.derive(KdfSpec.pbkdf2(KdfHash.SHA256, salt, 1, 32), pw);
        byte[] dk2 = KdfKit.derive(KdfSpec.pbkdf2(KdfHash.SHA256, salt, 2, 32), pw);
        assertEquals(32, dk1.length);
        assertEquals(32, dk2.length);
        boolean diff = false;
        for (int i = 0; i < 32; i++) {
            if (dk1[i] != dk2[i]) {
                diff = true;
                break;
            }
        }
        assertEquals("不同迭代次数输出应不同", true, diff);
    }

    @Test
    public void pbkdf2_nullIkm_throws() {
        try {
            KdfKit.derive(KdfSpec.pbkdf2(KdfHash.SHA256, new byte[]{1}, 1, 32), null);
            fail("ikm 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: input key material must not be null", e.getMessage());
        }
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
