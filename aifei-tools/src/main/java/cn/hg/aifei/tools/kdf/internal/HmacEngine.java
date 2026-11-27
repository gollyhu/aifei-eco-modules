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
 * HMAC 派生原语（RFC 2104 / RFC 4231）：{@code HMAC(secret=ikm, data=message) → key}。
 *
 * <p>对齐 RFC 2104 {@code HMAC(K, text)} 两入参：secret=ikm（K）、data=message（text）。
 * data 传 null 视为空字节（{@code HMAC(ikm, "")}）。keyLen 语义：
 * 原始输出 = HashLen（SHA-256=32 / SHA-512=64）；keyLen≤HashLen 截取前 keyLen 字节；
 * keyLen&gt;HashLen 按 RFC 5869 expand 风格循环扩展（HMAC 作 PRF，PRK=原始输出）。</p>
 */
public final class HmacEngine {

    private HmacEngine() {
    }

    /**
     * 用 HMAC 派生密钥。
     *
     * @param spec 派生参数（algorithm = HMAC）
     * @param ikm secret（输入密钥材料，RFC 2104 的 K）
     * @param data message（RFC 2104 的 text），null 视为空字节
     * @return keyLen 字节密钥
     * @throws cn.hg.aifei.tools.kdf.KdfException 算法不可用或派生失败
     */
    public static byte[] derive(KdfSpec spec, byte[] ikm, byte[] data) {
        KdfHash hash = spec.getHash();
        String macAlg = "Hmac" + hash.name();
        Mac mac;
        try {
            mac = Mac.getInstance(macAlg);
        } catch (NoSuchAlgorithmException e) {
            throw KdfErrors.algorithmNotAvailable(macAlg, e);
        }
        try {
            mac.init(new SecretKeySpec(ikm, macAlg));
        } catch (java.security.InvalidKeyException e) {
            throw KdfErrors.deriveFailed("HMAC", hash.name(), e);
        }
        // 原始输出 H0 = HMAC(secret=ikm, data=message)，data=null 视空字节
        byte[] h0 = mac.doFinal(data == null ? new byte[0] : data);
        int hashLen = mac.getMacLength();
        int keyLen = spec.getKeyLen();
        if (keyLen <= hashLen) {
            // keyLen≤HashLen：截取前 keyLen 字节
            byte[] out = new byte[keyLen];
            System.arraycopy(h0, 0, out, 0, keyLen);
            return out;
        }
        // keyLen>HashLen：按 RFC 5869 expand 风格循环扩展（PRK=H0，HMAC 作 PRF）
        int max = 255 * hashLen;
        if (keyLen > max) {
            throw KdfErrors.hkdfKeyLenExceedsMax(keyLen, max, hash.name());
        }
        byte[] okm = new byte[keyLen];
        System.arraycopy(h0, 0, okm, 0, hashLen);
        // RFC 5869 expand 风格：T(0)=空字节，T(i)=HMAC(PRK=h0, T(i-1)‖counter)
        byte[] t = new byte[0];
        int pos = hashLen;
        int counter = 1;
        Mac expandMac;
        try {
            expandMac = Mac.getInstance(macAlg);
        } catch (NoSuchAlgorithmException e) {
            throw KdfErrors.algorithmNotAvailable(macAlg, e);
        }
        try {
            expandMac.init(new SecretKeySpec(h0, macAlg));
        } catch (java.security.InvalidKeyException e) {
            throw KdfErrors.deriveFailed("HMAC", hash.name(), e);
        }
        while (pos < keyLen) {
            expandMac.reset();
            expandMac.update(t);
            expandMac.update((byte) counter);
            t = expandMac.doFinal();
            int take = Math.min(hashLen, keyLen - pos);
            System.arraycopy(t, 0, okm, pos, take);
            pos += take;
            counter++;
        }
        return okm;
    }
}
