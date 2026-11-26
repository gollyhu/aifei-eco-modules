/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.math.BigDecimal;

/**
 * 金额换算与校验的唯一实现：入参用「分」，出参用内核的「元」字符串。
 *
 * <p>换算全程走 {@link BigDecimal}，禁止 {@code double} / {@code float}——浮点二进制表示
 * 无法精确表达 0.01 这类十进制小数，会在比价与对账时产生误差。</p>
 */
public final class PayAmounts {

    /**
     * 一分对应的元的小数位数。
     */
    private static final int YUAN_SCALE = 2;

    /**
     * 禁止创建工具类实例。
     */
    private PayAmounts() {
    }

    /**
     * 分 → 元字符串（两位小数）。
     *
     * @param fen 金额，单位分
     * @return 形如 {@code "0.01"} / {@code "1000.00"} 的元字符串
     */
    public static String fenToYuan(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(YUAN_SCALE).toPlainString();
    }

    /**
     * 校验金额为正数。
     *
     * @param fen  金额，单位分
     * @param name 参数名，用于异常消息
     * @throws IllegalArgumentException {@code fen <= 0}
     */
    public static void requirePositive(long fen, String name) {
        if (fen <= 0L) {
            throw new IllegalArgumentException(name + " 必须大于 0（单位：分），实际为 " + fen);
        }
    }
}
