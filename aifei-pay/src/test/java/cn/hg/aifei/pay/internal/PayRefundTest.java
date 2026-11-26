/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.model.PayRefundResult;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestProps;
import cn.aifei.log.LogFactory;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 退款动作（AC-09）：退款结果字段、金额换算、退款单号作为幂等键**原样透传**（模块不去重）。
 */
public class PayRefundTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 商户订单号。
     */
    private static final String OUT_TRADE_NO = "T2026010100001";

    /**
     * 商户退款单号（幂等键）。
     */
    private static final String OUT_REQUEST_NO = "R2026010100001";

    /**
     * 换入前的日志工厂。
     */
    private LogFactory previousLogFactory;

    /**
     * 内核替身。
     */
    private PayKernelStub stub;

    /**
     * 被测客户端。
     */
    private DefaultPayClient client;

    /**
     * 装配默认配置与伪内核。
     */
    @Before
    public void setUp() {
        previousLogFactory = NoOpLogFactory.install();
        stub = new PayKernelStub();
        client = new DefaultPayClient(PayConfig.parse(PayTestProps.keyMode(APP)), stub);
    }

    /**
     * 还原日志工厂。
     */
    @After
    public void tearDown() {
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * AC-09：退款成功 → 返回退款结果值对象，字段原样透出且回显幂等键。
     */
    @Test
    public void shouldReturnRefundResultFields() {
        PayRefundResult result = client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 1L, "用户申请退款");

        assertEquals("商户订单号", OUT_TRADE_NO, result.outTradeNo());
        assertEquals("支付宝交易号", "2026010122001445101000000001", result.tradeNo());
        assertEquals("退款单号回显", OUT_REQUEST_NO, result.outRequestNo());
        assertEquals("退款金额（元）", "0.01", result.refundFee());
        assertEquals("资金变动方向", "Y", result.fundChange());
        assertEquals("退款时间", "2026-11-26 10:05:00", result.gmtRefundPay());
    }

    /**
     * AC-09：同一退款单号重复提交 → 两次都真实调用内核，且 {@code out_request_no} 完全一致（模块不去重）。
     */
    @Test
    public void shouldPassIdenticalOutRequestNoOnRepeatedRefund() {
        PayRefundResult first = client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 1L, null);
        PayRefundResult second = client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 1L, null);

        assertEquals("模块不得拦截重复退款，两次都应到达内核", 2, stub.callCount());
        Map<String, Object> firstParams = stub.calls().get(0).params();
        Map<String, Object> secondParams = stub.calls().get(1).params();
        assertEquals("第一次透传的幂等键", OUT_REQUEST_NO, firstParams.get("out_request_no"));
        assertEquals("第二次透传的幂等键", OUT_REQUEST_NO, secondParams.get("out_request_no"));
        assertEquals("幂等键回显应一致", first.outRequestNo(), second.outRequestNo());
    }

    /**
     * AC-09：退款金额按「分 → 元」换算。
     */
    @Test
    public void shouldConvertRefundAmountToYuan() {
        client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 12345L, null);

        assertEquals("退款金额应换算为元字符串", "123.45", stub.lastCall().param("refund_amount"));
        assertEquals("订单号应透传", OUT_TRADE_NO, stub.lastCall().param("out_trade_no"));
        assertEquals("退款单号应透传", OUT_REQUEST_NO, stub.lastCall().param("out_request_no"));
    }

    /**
     * AC-09：退款原因可空——空/空白时不提交该字段，非空时去除首尾空白后提交。
     */
    @Test
    public void shouldOmitBlankRefundReason() {
        client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 1L, null);
        assertFalse("空原因不应提交 refund_reason", stub.lastCall().params().containsKey("refund_reason"));

        client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 1L, "   ");
        assertFalse("空白原因不应提交 refund_reason", stub.lastCall().params().containsKey("refund_reason"));

        client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 1L, "  商品缺货  ");
        assertEquals("非空原因应去除首尾空白", "商品缺货", stub.lastCall().param("refund_reason"));
    }

    /**
     * AC-09：退款单号必填且不超长，违反属调用方写错。
     */
    @Test
    public void shouldRejectInvalidOutRequestNo() {
        assertThrows(IllegalArgumentException.class, () -> client.refund(APP, OUT_TRADE_NO, null, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> client.refund(APP, OUT_TRADE_NO, "  ", 1L, null));
        assertEquals("校验失败不应触达内核", 0, stub.callCount());
    }

    /**
     * AC-09：退款金额必须大于 0。
     */
    @Test
    public void shouldRejectNonPositiveRefundAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, 0L, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.refund(APP, OUT_TRADE_NO, OUT_REQUEST_NO, -1L, null));
        assertTrue("校验失败不应触达内核", stub.callCount() == 0);
    }
}
