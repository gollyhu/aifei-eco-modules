/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * KdfKit 门面分派与参数非法路径测试。
 */
public class KdfKitTest {

    @Test
    public void dispatch_hkdf() {
        KdfSpec spec = KdfSpec.hkdf(KdfHash.SHA256, new byte[]{1}, new byte[]{2}, 32);
        byte[] key = KdfKit.derive(spec, new byte[]{3, 4});
        assertEquals(32, key.length);
    }

    @Test
    public void dispatch_pbkdf2() {
        KdfSpec spec = KdfSpec.pbkdf2(KdfHash.SHA256, new byte[]{1}, 100, 32);
        byte[] key = KdfKit.derive(spec, "password".getBytes());
        assertEquals(32, key.length);
    }

    @Test
    public void dispatch_hmac() {
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 32);
        byte[] key = KdfKit.derive(spec, new byte[]{1, 2, 3});
        assertEquals(32, key.length);
    }

    @Test
    public void dispatch_hmac_threeArg() {
        // 三参重载：data=message 路径
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 32);
        byte[] key = KdfKit.derive(spec, new byte[]{1, 2, 3}, new byte[]{4, 5});
        assertEquals(32, key.length);
    }

    @Test
    public void dispatch_hmac_keyLenTruncate() {
        // keyLen=16 < HashLen=32，截取前 16 字节
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 16);
        assertEquals(16, KdfKit.derive(spec, new byte[]{1}).length);
    }

    @Test
    public void dispatch_hmac_keyLenExpand() {
        // keyLen=50 > HashLen=32，expand 扩展
        KdfSpec spec = KdfSpec.hmac(KdfHash.SHA256, 50);
        assertEquals(50, KdfKit.derive(spec, new byte[]{1}).length);
    }

    @Test
    public void dispatch_pbkdf2_sha512() {
        KdfSpec spec = KdfSpec.pbkdf2(KdfHash.SHA512, new byte[]{1}, 100, 64);
        byte[] key = KdfKit.derive(spec, "password".getBytes());
        assertEquals(64, key.length);
    }

    @Test
    public void nullSpec_throws() {
        try {
            KdfKit.derive(null, new byte[]{1});
            fail("spec 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: spec must not be null", e.getMessage());
        }
    }

    @Test
    public void nullIkm_throws() {
        try {
            KdfKit.derive(KdfSpec.hmac(KdfHash.SHA256, 32), null);
            fail("ikm 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: input key material must not be null", e.getMessage());
        }
    }

    @Test
    public void hkdf_keyLenZero_throws() {
        try {
            KdfSpec.hkdf(KdfHash.SHA256, null, null, 0);
            fail("keyLen 非正应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: keyLen must be positive, but was: 0", e.getMessage());
        }
    }

    @Test
    public void hkdf_keyLenNegative_throws() {
        try {
            KdfSpec.hkdf(KdfHash.SHA256, null, null, -1);
            fail("keyLen 非正应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: keyLen must be positive, but was: -1", e.getMessage());
        }
    }

    @Test
    public void pbkdf2_nullSalt_throws() {
        try {
            KdfSpec.pbkdf2(KdfHash.SHA256, null, 100, 32);
            fail("PBKDF2 salt 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: salt must not be null for PBKDF2", e.getMessage());
        }
    }

    @Test
    public void pbkdf2_iterationsZero_throws() {
        try {
            KdfSpec.pbkdf2(KdfHash.SHA256, new byte[]{1}, 0, 32);
            fail("iterations 非正应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: iterations must be positive, but was: 0", e.getMessage());
        }
    }

    @Test
    public void pbkdf2_iterationsNegative_throws() {
        try {
            KdfSpec.pbkdf2(KdfHash.SHA256, new byte[]{1}, -5, 32);
            fail("iterations 非正应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: iterations must be positive, but was: -5", e.getMessage());
        }
    }

    @Test
    public void pbkdf2_keyLenZero_throws() {
        try {
            KdfSpec.pbkdf2(KdfHash.SHA256, new byte[]{1}, 100, 0);
            fail("keyLen 非正应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: keyLen must be positive, but was: 0", e.getMessage());
        }
    }

    @Test
    public void hmac_keyLenZero_throws() {
        try {
            KdfSpec.hmac(KdfHash.SHA256, 0);
            fail("keyLen 非正应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: keyLen must be positive, but was: 0", e.getMessage());
        }
    }

    @Test
    public void hkdf_nullHash_throws() {
        try {
            KdfSpec.hkdf(null, null, null, 32);
            fail("hash 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: hash must not be null", e.getMessage());
        }
    }

    @Test
    public void pbkdf2_nullHash_throws() {
        try {
            KdfSpec.pbkdf2(null, new byte[]{1}, 100, 32);
            fail("hash 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: hash must not be null", e.getMessage());
        }
    }

    @Test
    public void hmac_nullHash_throws() {
        try {
            KdfSpec.hmac(null, 32);
            fail("hash 为 null 应抛异常");
        } catch (KdfException e) {
            assertEquals("kdf: hash must not be null", e.getMessage());
        }
    }

    @Test
    public void hkdf_arbitraryKeyLen() {
        // 任意 keyLen 输出（跨块）
        assertEquals(1, KdfKit.derive(KdfSpec.hkdf(KdfHash.SHA256, null, null, 1),
                new byte[]{1}).length);
        assertEquals(100, KdfKit.derive(KdfSpec.hkdf(KdfHash.SHA256, null, null, 100),
                new byte[]{1}).length);
        assertEquals(500, KdfKit.derive(KdfSpec.hkdf(KdfHash.SHA512, new byte[]{1}, new byte[]{2}, 500),
                new byte[]{1}).length);
    }
}
