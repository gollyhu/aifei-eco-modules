/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

/**
 * 敏感值脱敏的唯一实现：把「键名 + 值」渲染成可直接放进异常消息与日志的字符串。
 *
 * <p>全模块只有本类与 {@link PayErrors} 可以拼接配置值到消息里；其余类拼接配置值一律违规。</p>
 */
public final class PayMask {

    /**
     * 完全隐藏时的替代文本。
     */
    private static final String MASKED = "***";

    /**
     * 凭据类键名的后缀：值一律完全不显示。
     */
    private static final String[] CREDENTIAL_SUFFIXES = {
            "private-key",
            "app-cert",
            "root-cert",
            ".value"
    };

    /**
     * 标识类键名的后缀：值原样显示（不构成凭据泄漏）。
     */
    private static final String[] IDENTIFIER_SUFFIXES = {
            "app-id",
            "appId",
            "env",
            "sign-mode",
            "sign-type",
            "charset",
            "gateway-url",
            "connect-timeout",
            "read-timeout",
            "page-http-method"
    };

    /**
     * 证书序列号键名的后缀：只显示前缀。
     */
    private static final String CERT_SN_SUFFIX = "cert-sn";

    /**
     * 证书序列号显示的前缀长度。
     */
    private static final int CERT_SN_PREFIX_LENGTH = 8;

    /**
     * 省略号。
     */
    private static final String ELLIPSIS = "...";

    /**
     * 禁止创建工具类实例。
     */
    private PayMask() {
    }

    /**
     * 按设计 §6.6 的分类规则脱敏一个键值对。
     *
     * @param key   配置键名（或语义等价的标识名）
     * @param value 原始值，可为 {@code null}
     * @return 可直接放进异常消息的字符串
     */
    public static String mask(String key, String value) {
        if (key == null) {
            return MASKED;
        }
        if (isCredentialKey(key)) {
            return MASKED + "（长度 " + lengthOf(value) + "）";
        }
        if (key.endsWith(CERT_SN_SUFFIX)) {
            return certSn(value);
        }
        if (endsWithAny(key, IDENTIFIER_SUFFIXES)) {
            return value == null ? "" : value;
        }
        return MASKED;
    }

    /**
     * 判断是否为凭据类键名（值为私钥、证书或验签凭据）。
     *
     * @param key 配置键名
     * @return 属于凭据类时 {@code true}
     */
    private static boolean isCredentialKey(String key) {
        return key != null && endsWithAny(key, CREDENTIAL_SUFFIXES);
    }

    /**
     * 证书序列号的显示形态：前 8 位加省略号，便于比对轮换而不泄漏完整值。
     *
     * @param certSn 证书序列号，可为 {@code null}
     * @return 脱敏后的证书序列号
     */
    public static String certSn(String certSn) {
        if (certSn == null || certSn.isEmpty()) {
            return "";
        }
        if (certSn.length() <= CERT_SN_PREFIX_LENGTH) {
            return certSn;
        }
        return certSn.substring(0, CERT_SN_PREFIX_LENGTH) + ELLIPSIS;
    }

    /**
     * 计算值的长度，{@code null} 视为 0。
     *
     * @param value 原始值
     * @return 长度
     */
    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    /**
     * 判断键名是否以给定后缀之一结尾。
     *
     * @param key      键名
     * @param suffixes 后缀数组
     * @return 命中任一后缀时 {@code true}
     */
    private static boolean endsWithAny(String key, String[] suffixes) {
        for (String suffix : suffixes) {
            if (key.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
