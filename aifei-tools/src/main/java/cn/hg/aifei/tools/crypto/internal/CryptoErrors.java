/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto.internal;

import cn.hg.aifei.tools.crypto.CryptoException;

/**
 * crypto 异常消息与异常构造的唯一定义点。
 */
public final class CryptoErrors {

    private CryptoErrors() {
    }

    /**
     * 摘要文本为 null。
     *
     * @return 统一异常
     */
    public static CryptoException nullText() {
        return new CryptoException("crypto: text must not be null");
    }

    /**
     * 消息认证码密钥为 null。
     *
     * @return 统一异常
     */
    public static CryptoException nullKey() {
        return new CryptoException("crypto: key must not be null");
    }

    /**
     * 消息认证码消息为 null。
     *
     * @return 统一异常
     */
    public static CryptoException nullData() {
        return new CryptoException("crypto: data must not be null");
    }

    /**
     * 底层 JDK 算法不可用。
     *
     * @param alg 算法名
     * @param cause 根因
     * @return 统一异常
     */
    public static CryptoException algorithmNotAvailable(String alg, Throwable cause) {
        return new CryptoException("crypto: algorithm \"" + alg
                + "\" not available in this JDK, cause: " + detail(cause), cause);
    }

    /**
     * 消息认证码计算失败。
     *
     * @param cause 根因
     * @return 统一异常
     */
    public static CryptoException hmacFailed(Throwable cause) {
        return new CryptoException("crypto: hmac failed, cause: " + detail(cause), cause);
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
