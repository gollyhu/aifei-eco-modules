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
 * PBKDF2（RFC 8018 §5.2）实现，纯 JDK javax.crypto.Mac 手动 PRF 循环。
 *
 * <p>实现采用手动 HMAC PRF 循环，纯 JDK、零第三方，遵循 RFC 8018 §5.2。未使用
 * {@code SecretKeyFactory.getInstance("PBKDF2WithHmac"+hash)} + {@code PBEKeySpec} +
 * {@code generateSecret().getEncoded()}：在 JDK 19 SunJCE 上
 * {@code PBKDF2SecretKey.getEncoded()} 返回的字节长度由 PBE cipher 约束决定（默认 8 字节），
 * 不等于 {@code PBEKeySpec} 请求的 keyLen，无法满足「任意 keyLen 输出」契约。
 * 实现将 {@code javax.crypto.} / {@code java.security.} 收敛在 kdf/internal。</p>
 */
public final class Pbkdf2Engine {

    private Pbkdf2Engine() {
    }

    /**
     * 按 RFC 8018 §5.2 派生密钥。
     *
     * @param spec 派生参数（algorithm = PBKDF2）
     * @param ikm 口令（password）
     * @return keyLen 字节密钥
     * @throws cn.hg.aifei.tools.kdf.KdfException 算法不可用或派生失败
     */
    public static byte[] derive(KdfSpec spec, byte[] ikm) {
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
            throw KdfErrors.deriveFailed("PBKDF2", hash.name(), e);
        }
        byte[] salt = spec.getSalt();
        int iterations = spec.getIterations();
        int keyLen = spec.getKeyLen();
        int hLen = mac.getMacLength();
        // DK = T_1 || T_2 || ... || T_l，截断到 keyLen
        int blocks = (keyLen + hLen - 1) / hLen;
        byte[] dk = new byte[keyLen];
        byte[] blockIndex = new byte[4];
        int pos = 0;
        for (int block = 1; block <= blocks; block++) {
            blockIndex[0] = (byte) (block >>> 24);
            blockIndex[1] = (byte) (block >>> 16);
            blockIndex[2] = (byte) (block >>> 8);
            blockIndex[3] = (byte) block;
            // U_1 = PRF(P, S || INT_32_BE(block))
            mac.reset();
            mac.update(salt);
            mac.update(blockIndex);
            byte[] u = mac.doFinal();
            byte[] t = u.clone();
            // U_2..U_c: U_j = PRF(P, U_{j-1})，累异或
            for (int j = 1; j < iterations; j++) {
                mac.reset();
                u = mac.doFinal(u);
                for (int k = 0; k < hLen; k++) {
                    t[k] ^= u[k];
                }
            }
            int take = Math.min(hLen, keyLen - pos);
            System.arraycopy(t, 0, dk, pos, take);
            pos += take;
        }
        return dk;
    }
}
