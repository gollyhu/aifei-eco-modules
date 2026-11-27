/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

import cn.hg.aifei.tools.kdf.internal.HkdfEngine;
import cn.hg.aifei.tools.kdf.internal.HmacEngine;
import cn.hg.aifei.tools.kdf.internal.KdfErrors;
import cn.hg.aifei.tools.kdf.internal.Pbkdf2Engine;

/**
 * KDF 全静态门面：按 {@link KdfSpec} 的 algorithm 维分派到 internal 引擎。
 *
 * <p>HKDF 走 {@link HkdfEngine}（RFC 5869 extract+expand）、
 * PBKDF2 走 {@link Pbkdf2Engine}（RFC 8018 §5.2 手动 PRF 循环）、
 * HMAC 走 {@link HmacEngine}（RFC 2104 {@code HMAC(K, text)} 派生原语）；
 * ikm = 输入密钥材料（HKDF 的 IKM / PBKDF2 的 password / HMAC 的 secret=K）。</p>
 *
 * <p>两个 derive 重载：两参 {@code derive(spec, ikm)} 等价
 * {@code data=null}——HMAC 视空字节 {@code HMAC(ikm, "")}、HKDF/PBKDF2 忽略 data；
 * 三参 {@code derive(spec, ikm, data)} 的 data 是 HMAC 的 message（RFC 2104 text），
 * HKDF/PBKDF2 忽略 data，层B {@code KDF(sn, challenge)} 传入 {@code ikm=sn}（secret）、
 * {@code data=challenge}（message）。</p>
 */
public final class KdfKit {

    private KdfKit() {
    }

    /**
     * 按 spec.algorithm 派生密钥（两参便捷重载，等价 data=null）。
     *
     * <p>HMAC 视 data 为空字节 {@code HMAC(ikm, "")}；HKDF/PBKDF2 忽略 data。</p>
     *
     * @param spec 派生参数，不得为 null
     * @param ikm 输入密钥材料，不得为 null
     * @return keyLen 字节密钥
     * @throws KdfException spec / ikm 为 null、algorithm 非法或派生失败
     */
    public static byte[] derive(KdfSpec spec, byte[] ikm) {
        return derive(spec, ikm, null);
    }

    /**
     * 按 spec.algorithm 派生密钥（三参重载）。
     *
     * <p>data 是 HMAC 的 message（RFC 2104 {@code HMAC(K, text)} 的 text）；
     * HKDF/PBKDF2 忽略 data（传 null 即可）；HMAC 传 null 视为空字节。</p>
     *
     * @param spec 派生参数，不得为 null
     * @param ikm 输入密钥材料，不得为 null（HKDF 的 IKM / PBKDF2 的 password / HMAC 的 secret=K）
     * @param data HMAC 的 message（RFC 2104 text），HMAC 传 null 视空字节；HKDF/PBKDF2 忽略
     * @return keyLen 字节密钥
     * @throws KdfException spec / ikm 为 null、algorithm 非法或派生失败
     */
    public static byte[] derive(KdfSpec spec, byte[] ikm, byte[] data) {
        if (spec == null) {
            throw KdfErrors.nullSpec();
        }
        if (ikm == null) {
            throw KdfErrors.nullIkm();
        }
        String alg = spec.getAlgorithm();
        if (KdfSpec.HKDF.equals(alg)) {
            // HKDF 忽略 data，info 在 spec
            return HkdfEngine.derive(spec, ikm);
        }
        if (KdfSpec.PBKDF2.equals(alg)) {
            // PBKDF2 忽略 data，salt/iterations 在 spec
            return Pbkdf2Engine.derive(spec, ikm);
        }
        if (KdfSpec.HMAC.equals(alg)) {
            // HMAC：ikm=secret、data=message（RFC 2104）
            return HmacEngine.derive(spec, ikm, data);
        }
        throw KdfErrors.unknownAlgorithm(alg);
    }
}
