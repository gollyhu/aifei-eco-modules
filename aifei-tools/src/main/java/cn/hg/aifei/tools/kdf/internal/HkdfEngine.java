/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf.internal;

import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import cn.hg.aifei.tools.kdf.KdfHash;
import cn.hg.aifei.tools.kdf.KdfSpec;

/**
 * HKDF（RFC 5869）extract+expand 实现，纯 JDK javax.crypto.Mac。
 *
 * <p>extract: {@code HMAC(salt, IKM) → PRK}，salt 为空时用全 0 HashLen 字节；
 * expand: {@code HMAC(PRK, info‖counter)} 循环到 keyLen，counter 从 1 起单字节。</p>
 */
public final class HkdfEngine {

    private HkdfEngine() {
    }

    /**
     * 按 RFC 5869 派生密钥。
     *
     * @param spec 派生参数（algorithm = HKDF）
     * @param ikm 输入密钥材料
     * @return keyLen 字节密钥
     * @throws cn.hg.aifei.tools.kdf.KdfException 算法不可用或派生失败
     */
    public static byte[] derive(KdfSpec spec, byte[] ikm) {
        KdfHash hash = spec.getHash();
        String macAlg = macAlgorithm(hash);
        Mac mac = newMac(macAlg, hash, "HKDF");
        int hashLen = mac.getMacLength();
        byte[] salt = spec.getSalt();
        if (salt == null) {
            salt = new byte[hashLen];
        }
        // extract: PRK = HMAC(salt, IKM)
        byte[] prk = hmac(mac, macAlg, salt, ikm, hash, "HKDF");
        int keyLen = spec.getKeyLen();
        int max = 255 * hashLen;
        if (keyLen > max) {
            throw KdfErrors.hkdfKeyLenExceedsMax(keyLen, max, hash.name());
        }
        // expand: T(0)=空; T(i)=HMAC(PRK, T(i-1)‖info‖counter); OKM=前 keyLen 字节
        byte[] info = spec.getInfo();
        if (info == null) {
            info = new byte[0];
        }
        Mac expandMac = newMac(macAlg, hash, "HKDF");
        try {
            expandMac.init(new SecretKeySpec(prk, macAlg));
        } catch (java.security.InvalidKeyException e) {
            throw KdfErrors.deriveFailed("HKDF", hash.name(), e);
        }
        byte[] okm = new byte[keyLen];
        byte[] t = new byte[0];
        int pos = 0;
        int counter = 1;
        while (pos < keyLen) {
            expandMac.reset();
            expandMac.update(t);
            expandMac.update(info);
            expandMac.update((byte) counter);
            t = expandMac.doFinal();
            int take = Math.min(hashLen, keyLen - pos);
            System.arraycopy(t, 0, okm, pos, take);
            pos += take;
            counter++;
        }
        return okm;
    }

    private static byte[] hmac(Mac reused, String macAlg, byte[] key, byte[] data, KdfHash hash, String alg) {
        // extract 阶段需要独立 Mac 实例（与 expand 的 init 状态隔离）
        Mac mac = newMac(macAlg, hash, alg);
        try {
            mac.init(new SecretKeySpec(key, macAlg));
        } catch (java.security.InvalidKeyException e) {
            throw KdfErrors.deriveFailed(alg, hash.name(), e);
        }
        return mac.doFinal(data);
    }

    static String macAlgorithm(KdfHash hash) {
        return "Hmac" + hash.name();
    }

    static Mac newMac(String macAlg, KdfHash hash, String alg) {
        try {
            return Mac.getInstance(macAlg);
        } catch (NoSuchAlgorithmException e) {
            throw KdfErrors.algorithmNotAvailable(macAlg, e);
        }
    }
}
