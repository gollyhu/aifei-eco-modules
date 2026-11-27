/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.crypto;

import cn.hg.aifei.tools.crypto.internal.ConstantTimeEngine;
import cn.hg.aifei.tools.crypto.internal.HmacEngine;
import cn.hg.aifei.tools.crypto.internal.Sha1Engine;

/**
 * 摘要、消息认证码与常量时间比较的无状态工具，全部方法均为静态方法。
 *
 * <p>字符编码固定为 UTF-8，不暴露字符集参数；失败统一抛 {@link CryptoException}，
 * 公共方法不声明受检异常。本类没有实例字段，可多线程并发调用。</p>
 */
public final class CryptoUtil {

    private CryptoUtil() {
    }

    /**
     * 计算文本的 SHA-1 摘要，输出 40 位小写十六进制字符串。
     *
     * @param text 待摘要文本，不得为 null，空串合法
     * @return 40 位小写十六进制摘要
     * @throws CryptoException text 为 null，或底层摘要算法不可用
     */
    public static String sha1Hex(String text) {
        return Sha1Engine.sha1Hex(text);
    }

    /**
     * 计算文本的 SHA-1 摘要，输出标准 Base64 字符串（带填充）。
     *
     * @param text 待摘要文本，不得为 null，空串合法
     * @return 标准 Base64 编码的摘要
     * @throws CryptoException text 为 null，或底层摘要算法不可用
     */
    public static String sha1Base64(String text) {
        return Sha1Engine.sha1Base64(text);
    }

    /**
     * 计算 HMAC-SHA1，输出标准 Base64 字符串（带填充）。
     *
     * <p>参数顺序遵循 RFC 2104 的 {@code HMAC(K, text)}：key 为密钥 K，data 为消息 text。</p>
     *
     * @param key 密钥文本，不得为 null，空串合法
     * @param data 消息文本，不得为 null，空串合法
     * @return 标准 Base64 编码的认证码
     * @throws CryptoException key 或 data 为 null，参数非法，或底层算法不可用
     */
    public static String hmacSha1(String key, String data) {
        return HmacEngine.hmacSha1(key, data);
    }

    /**
     * 计算 HMAC-SHA256，输出标准 Base64 字符串（带填充）。
     *
     * <p>参数顺序遵循 RFC 2104 的 {@code HMAC(K, text)}：key 为密钥 K，data 为消息 text。</p>
     *
     * @param key 密钥文本，不得为 null，空串合法
     * @param data 消息文本，不得为 null，空串合法
     * @return 标准 Base64 编码的认证码
     * @throws CryptoException key 或 data 为 null，参数非法，或底层算法不可用
     */
    public static String hmacSha256(String key, String data) {
        return HmacEngine.hmacSha256(key, data);
    }

    /**
     * 以常量时间方式比较两个文本是否相等。
     *
     * <p>任一方为 null 时直接返回 false，不抛异常；长度不同也走常量时间路径，
     * 不因前缀差异提前返回。两个空串视为相等。</p>
     *
     * @param expected 期望文本，允许为 null
     * @param actual 实际文本，允许为 null
     * @return 两者 UTF-8 字节逐位相等且均非 null 时返回 true，否则返回 false
     */
    public static boolean constantTimeEquals(String expected, String actual) {
        return ConstantTimeEngine.constantTimeEquals(expected, actual);
    }
}
