/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf.internal;

import cn.hg.aifei.tools.kdf.KdfException;

/**
 * kdf 异常消息与构造的唯一定义点（设计 §7.2 措辞唯一来源）。
 */
public final class KdfErrors {

    private KdfErrors() {
    }

    /**
     * spec 为 null。
     *
     * @return 统一异常
     */
    public static KdfException nullSpec() {
        return new KdfException("kdf: spec must not be null");
    }

    /**
     * 输入密钥材料为 null。
     *
     * @return 统一异常
     */
    public static KdfException nullIkm() {
        return new KdfException("kdf: input key material must not be null");
    }

    /**
     * 算法名非法。
     *
     * @param alg 实际值
     * @return 统一异常
     */
    public static KdfException unknownAlgorithm(String alg) {
        return new KdfException("kdf: unknown algorithm \"" + alg
                + "\", expected one of: HKDF, PBKDF2, HMAC");
    }

    /**
     * keyLen 非正。
     *
     * @param n 实际值
     * @return 统一异常
     */
    public static KdfException keyLenNotPositive(int n) {
        return new KdfException("kdf: keyLen must be positive, but was: " + n);
    }

    /**
     * PBKDF2 salt 为 null。
     *
     * @return 统一异常
     */
    public static KdfException pbkdf2NullSalt() {
        return new KdfException("kdf: salt must not be null for PBKDF2");
    }

    /**
     * PBKDF2 iterations 非正。
     *
     * @param n 实际值
     * @return 统一异常
     */
    public static KdfException iterationsNotPositive(int n) {
        return new KdfException("kdf: iterations must be positive, but was: " + n);
    }

    /**
     * hash 档位为 null。
     *
     * @return 统一异常
     */
    public static KdfException hashNull() {
        return new KdfException("kdf: hash must not be null");
    }

    /**
     * HKDF keyLen 超过 RFC5869 上限（255 × HashLen）。
     *
     * @param keyLen 实际 keyLen
     * @param max 上限
     * @param hash 哈希档位
     * @return 统一异常
     */
    public static KdfException hkdfKeyLenExceedsMax(int keyLen, int max, String hash) {
        return new KdfException("kdf: keyLen " + keyLen + " exceeds RFC5869 maximum " + max + " for " + hash);
    }

    /**
     * 底层 JDK 算法不可用。
     *
     * @param alg 算法名
     * @param cause NoSuchAlgorithmException
     * @return 统一异常
     */
    public static KdfException algorithmNotAvailable(String alg, Throwable cause) {
        return new KdfException("kdf: algorithm \"" + alg + "\" not available in this JDK, cause: "
                + detail(cause), cause);
    }

    /**
     * 派生过程运行时异常。
     *
     * @param alg 算法名
     * @param hash 哈希档位
     * @param cause 原异常
     * @return 统一异常
     */
    public static KdfException deriveFailed(String alg, String hash, Throwable cause) {
        return new KdfException("kdf: derive failed for " + alg + "-" + hash + ", cause: "
                + detail(cause), cause);
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
