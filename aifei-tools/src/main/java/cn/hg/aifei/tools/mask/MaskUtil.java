/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.mask;

import cn.hg.aifei.tools.mask.internal.MaskErrors;

/**
 * 无状态脱敏工具：通用按位打码 + 九条默认规则转发。
 *
 * <p>所有默认规则方法都等价于 {@code use(规则名).apply(value)}，因此
 * {@link MaskKit#replace} 覆盖规则后自动跟随；value 为 null 返回 null、空串返回空串。</p>
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * 按保留位数打码，掩码字符为星号。
     *
     * @param value 原值
     * @param keepLeft 左侧保留位数
     * @param keepRight 右侧保留位数
     * @return 脱敏结果；value 为 null 返回 null，空串返回空串
     * @throws MaskException 保留位数为负
     */
    public static String mask(String value, int keepLeft, int keepRight) {
        return mask(value, keepLeft, keepRight, '*');
    }

    /**
     * 按保留位数与指定掩码字符打码。
     *
     * @param value 原值
     * @param keepLeft 左侧保留位数
     * @param keepRight 右侧保留位数
     * @param maskChar 掩码字符
     * @return 脱敏结果；value 为 null 返回 null，空串返回空串
     * @throws MaskException 保留位数为负
     */
    public static String mask(String value, int keepLeft, int keepRight, char maskChar) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return value;
        }
        if (keepLeft < 0 || keepRight < 0) {
            throw MaskErrors.negativeKeep();
        }
        int length = value.length();
        int left = Math.min(keepLeft, length);
        int right = Math.min(keepRight, length - left);
        int middle = length - left - right;
        StringBuilder out = new StringBuilder(length);
        out.append(value, 0, left);
        for (int i = 0; i < middle; i++) {
            out.append(maskChar);
        }
        out.append(value, length - right, length);
        return out.toString();
    }

    /**
     * 手机号脱敏：前 3 后 4。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskPhone(String value) {
        return MaskKit.use("phone").apply(value);
    }

    /**
     * 身份证号脱敏：前 6 后 4。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskIdCard(String value) {
        return MaskKit.use("idCard").apply(value);
    }

    /**
     * 银行卡号脱敏：前 6 后 4。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskBankCard(String value) {
        return MaskKit.use("bankCard").apply(value);
    }

    /**
     * 邮箱脱敏：@ 前保留首尾各 1 位，域名保留。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskEmail(String value) {
        return MaskKit.use("email").apply(value);
    }

    /**
     * 姓名脱敏：保留姓氏。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskName(String value) {
        return MaskKit.use("name").apply(value);
    }

    /**
     * 座机脱敏：保留区号（含 -）与后 4 位，无 - 时保留前 4 后 4。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskFixedPhone(String value) {
        return MaskKit.use("fixedPhone").apply(value);
    }

    /**
     * 密码脱敏：恒定六位掩码，不保留原长度。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskPassword(String value) {
        return MaskKit.use("password").apply(value);
    }

    /**
     * 地址脱敏：保留前 6 字符，其余等长掩码；长度不超过 6 时全打码。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskAddress(String value) {
        return MaskKit.use("address").apply(value);
    }

    /**
     * IP 脱敏：保留首段，其余每段一个掩码；按点或冒号分段，无分隔符时保留首字符。
     *
     * @param value 原值
     * @return 脱敏结果
     */
    public static String maskIp(String value) {
        return MaskKit.use("ip").apply(value);
    }
}
