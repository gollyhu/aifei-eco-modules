/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/**
 * 金额换算与校验（支撑 AC-07 / AC-09）：入参用「分」、报文用「元字符串」，且不得出现浮点误差。
 */
public class PayAmountTest {

    /**
     * 分 → 元：常见金额与边界金额。
     */
    @Test
    public void shouldConvertFenToYuan() {
        assertEquals("1 分", "0.01", PayAmounts.fenToYuan(1L));
        assertEquals("10 分", "0.10", PayAmounts.fenToYuan(10L));
        assertEquals("100 分", "1.00", PayAmounts.fenToYuan(100L));
        assertEquals("1111 分", "11.11", PayAmounts.fenToYuan(1111L));
        assertEquals("12345 分", "123.45", PayAmounts.fenToYuan(12345L));
        assertEquals("0 分（换算本身不做正值校验）", "0.00", PayAmounts.fenToYuan(0L));
        assertEquals("大额订单", "1000000000.00", PayAmounts.fenToYuan(100000000000L));
    }

    /**
     * 分 → 元：结果必须是两位小数的十进制定点文本，不出现浮点误差或科学计数法。
     */
    @Test
    public void shouldProduceExactDecimalTextWithoutFloatArtifacts() {
        assertEquals("3 分不得出现浮点误差", "0.03", PayAmounts.fenToYuan(3L));
        assertEquals("7 分不得出现浮点误差", "0.07", PayAmounts.fenToYuan(7L));
        assertEquals("29 分不得出现浮点误差", "0.29", PayAmounts.fenToYuan(29L));
        assertEquals("必须保持两位小数", "1.10", PayAmounts.fenToYuan(110L));
    }

    /**
     * 正值校验：0 与负数额度一律拒绝，正数放行。
     */
    @Test
    public void shouldRejectNonPositiveAmount() {
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> PayAmounts.requirePositive(0L, "amountFen"));
        assertEquals("amountFen 必须大于 0（单位：分），实际为 0", zero.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PayAmounts.requirePositive(-1L, "refundFen"));

        PayAmounts.requirePositive(1L, "amountFen");
    }
}
