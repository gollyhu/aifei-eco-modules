/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

import cn.hg.aifei.tools.kdf.internal.KdfErrors;

/**
 * KDF 派生参数值对象（algorithm + hash + salt + iterations + info + keyLen）。
 *
 * <p>三个静态工厂是构造唯一入口，私有构造器、值对象不可变。
 * HKDF：salt 可空（RFC 5869 默认全 0 HashLen 字节）、info 可空（作空字节）；
 * PBKDF2：salt 不可空、iterations &gt; 0；
 * HMAC：无 salt / iterations（getSalt 返回 null、getIterations 返回 0）。
 * 校验在工厂内完成，{@link KdfKit#derive} 不重复校验 spec 合法性。</p>
 */
public final class KdfSpec {

    /** 算法名合法值集合。 */
    public static final String HKDF = "HKDF";
    /** 算法名合法值集合。 */
    public static final String PBKDF2 = "PBKDF2";
    /** 算法名合法值集合。 */
    public static final String HMAC = "HMAC";

    private final String algorithm;
    private final KdfHash hash;
    private final byte[] salt;
    private final int iterations;
    private final byte[] info;
    private final int keyLen;

    private KdfSpec(String algorithm, KdfHash hash, byte[] salt, int iterations,
                    byte[] info, int keyLen) {
        this.algorithm = algorithm;
        this.hash = hash;
        // 防御性拷贝：salt/info 内部不可变
        this.salt = salt == null ? null : salt.clone();
        this.iterations = iterations;
        this.info = info == null ? null : info.clone();
        this.keyLen = keyLen;
    }

    /**
     * 构造 HKDF 派生参数。
     *
     * @param hash 哈希档位
     * @param salt 盐，可空（RFC 5869 默认全 0 HashLen 字节）
     * @param info 上下文信息，可空（作空字节）
     * @param keyLen 输出密钥字节长度，须大于 0
     * @return HKDF 参数
     * @throws KdfException hash 为 null 或 keyLen 非正
     */
    public static KdfSpec hkdf(KdfHash hash, byte[] salt, byte[] info, int keyLen) {
        if (hash == null) {
            throw KdfErrors.hashNull();
        }
        if (keyLen <= 0) {
            throw KdfErrors.keyLenNotPositive(keyLen);
        }
        return new KdfSpec(HKDF, hash, salt, 0, info, keyLen);
    }

    /**
     * 构造 PBKDF2 派生参数。
     *
     * @param hash 哈希档位
     * @param salt 盐，不可空
     * @param iterations 迭代次数，须大于 0
     * @param keyLen 输出密钥字节长度，须大于 0
     * @return PBKDF2 参数
     * @throws KdfException hash / salt 为 null、iterations 或 keyLen 非正
     */
    public static KdfSpec pbkdf2(KdfHash hash, byte[] salt, int iterations, int keyLen) {
        if (hash == null) {
            throw KdfErrors.hashNull();
        }
        if (salt == null) {
            throw KdfErrors.pbkdf2NullSalt();
        }
        if (iterations <= 0) {
            throw KdfErrors.iterationsNotPositive(iterations);
        }
        if (keyLen <= 0) {
            throw KdfErrors.keyLenNotPositive(keyLen);
        }
        return new KdfSpec(PBKDF2, hash, salt, iterations, null, keyLen);
    }

    /**
     * 构造 HMAC 派生原语参数。
     *
     * @param hash 哈希档位
     * @param keyLen 输出密钥字节长度，须大于 0
     * @return HMAC 参数
     * @throws KdfException hash 为 null 或 keyLen 非正
     */
    public static KdfSpec hmac(KdfHash hash, int keyLen) {
        if (hash == null) {
            throw KdfErrors.hashNull();
        }
        if (keyLen <= 0) {
            throw KdfErrors.keyLenNotPositive(keyLen);
        }
        return new KdfSpec(HMAC, hash, null, 0, null, keyLen);
    }

    /**
     * @return 算法名（HKDF / PBKDF2 / HMAC）
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * @return 哈希档位
     */
    public KdfHash getHash() {
        return hash;
    }

    /**
     * @return HKDF 可空；PBKDF2 非空；HMAC 返回 null（返回副本，外部修改不影响本对象）
     */
    public byte[] getSalt() {
        return salt == null ? null : salt.clone();
    }

    /**
     * @return 仅 PBKDF2 有效；HKDF / HMAC 返回 0
     */
    public int getIterations() {
        return iterations;
    }

    /**
     * @return 仅 HKDF 有效；PBKDF2 / HMAC 返回 null（返回副本，外部修改不影响本对象）
     */
    public byte[] getInfo() {
        return info == null ? null : info.clone();
    }

    /**
     * @return 输出密钥字节长度，恒大于 0
     */
    public int getKeyLen() {
        return keyLen;
    }
}
