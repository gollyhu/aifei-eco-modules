/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.kdf;

/**
 * KDF 哈希档位，决定 HMAC 与 PBKDF2 的底层算法后缀。
 */
public enum KdfHash {

    /** SHA-256，HMAC 输出 32 字节。 */
    SHA256,

    /** SHA-512，HMAC 输出 64 字节。 */
    SHA512
}
