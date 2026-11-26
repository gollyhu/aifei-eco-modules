/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.pay.AliPayException;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.hg.aifei.pay.support.PayTestProps;
import cn.aifei.log.LogFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 查单动作（AC-08）：交易字段透出，以及「订单不存在」的唯一口径——抛 {@link AliPayException} 且
 * {@code code() = ACQ.TRADE_NOT_EXIST}（不返回空结果形态）。
 */
public class PayQueryTest {

    /**
     * 被测应用编号。
     */
    private static final String APP = PayTestProps.APP_MAIN;

    /**
     * 商户订单号。
     */
    private static final String OUT_TRADE_NO = "T2026010100001";

    /**
     * 内核错误码：交易不存在。
     */
    private static final String CODE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

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
    private DefaultAliPayClient client;

    /**
     * 装配默认配置与伪内核。
     */
    @Before
    public void setUp() {
        previousLogFactory = NoOpLogFactory.install();
        stub = new PayKernelStub();
        client = new DefaultAliPayClient(PayConfig.parse(PayTestProps.keyMode(APP)), stub);
    }

    /**
     * 还原日志工厂。
     */
    @After
    public void tearDown() {
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * AC-08：存在订单 → 返回含交易状态 / 金额 / 交易号的公共值对象。
     */
    @Test
    public void shouldReturnTradeFieldsForExistingOrder() {
        PayQueryResult result = client.query(APP, OUT_TRADE_NO);

        assertEquals("商户订单号", OUT_TRADE_NO, result.outTradeNo());
        assertEquals("支付宝交易号", "2026010122001445101000000001", result.tradeNo());
        assertEquals("交易状态", "TRADE_SUCCESS", result.tradeStatus());
        assertEquals("订单金额（元，原样透出）", "0.01", result.totalAmount());
        assertEquals("买家实付金额", "0.01", result.buyerPayAmount());
        assertEquals("付款时间", "2026-11-26 10:00:00", result.sendPayDate());
        assertEquals("查单接口应收到订单号", OUT_TRADE_NO, stub.lastCall().param("out_trade_no"));
    }

    /**
     * AC-08：订单不存在 → 抛 {@link AliPayException}，{@code code()} 为内核错误码，且保留 cause。
     */
    @Test
    public void shouldThrowWithTradeNotExistCodeWhenOrderMissing() {
        PayKernelStub.KernelFault fault = new PayKernelStub.KernelFault(
                "业务处理失败", CODE_NOT_EXIST, "{\"code\":\"" + CODE_NOT_EXIST
                + "\",\"msg\":\"交易不存在\",\"sub_code\":\"ACQ.TRADE_NOT_EXIST\"}");
        stub.failingWith(fault);

        AliPayException e = assertThrows(AliPayException.class, () -> client.query(APP, OUT_TRADE_NO));

        assertEquals("错误码应透出内核业务码", CODE_NOT_EXIST, e.code());
        assertSame("原始异常应作为 cause 保留", fault, e.getCause());
        assertTrue("消息应含动作语义：" + e.getMessage(), e.getMessage().contains("查单"));
        assertTrue("消息应含错误码：" + e.getMessage(), e.getMessage().contains(CODE_NOT_EXIST));
        assertFalse("消息不得含内核异常类名：" + e.getMessage(),
                e.getMessage().contains("KernelFault"));
    }

    /**
     * AC-08：无法从内核异常提取错误码时 {@code code()} 为 {@code null}，不编造错误码。
     */
    @Test
    public void shouldLeaveCodeNullWhenKernelProvidesNoCode() {
        stub.failingWith(new IllegalStateException("网关连接中断"));

        AliPayException e = assertThrows(AliPayException.class, () -> client.query(APP, OUT_TRADE_NO));

        assertEquals("提取不到错误码时应为 null", null, e.code());
        assertTrue("消息应保留内核消息：" + e.getMessage(), e.getMessage().contains("网关连接中断"));
    }

    /**
     * AC-08 / AC-18：内核抛受检异常时也被收敛为 {@link AliPayException}，不外泄原始类型。
     */
    @Test
    public void shouldWrapCheckedKernelException() {
        stub.failingWith(new PayKernelStub.KernelFault("签名失败", null, null));

        AliPayException e = assertThrows(AliPayException.class, () -> client.query(APP, OUT_TRADE_NO));

        assertTrue("cause 应为内核原始异常", e.getCause() instanceof PayKernelStub.KernelFault);
    }

    /**
     * AC-08：响应缺字段时归一为空串，便于调用方直接比较。
     */
    @Test
    public void shouldNormalizeAbsentFieldsToEmptyString() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tradeStatus", "WAIT_BUYER_PAY");
        stub.queryResponse(response);

        PayQueryResult result = client.query(APP, OUT_TRADE_NO);

        assertEquals("缺失交易号为空串", "", result.tradeNo());
        assertEquals("缺失金额为空串", "", result.totalAmount());
        assertEquals("缺失付款时间为空串", "", result.sendPayDate());
        assertEquals("订单号回退为请求值", OUT_TRADE_NO, result.outTradeNo());
    }

    /**
     * 查单：订单号为空或超长属调用方写错。
     */
    @Test
    public void shouldRejectBlankOutTradeNo() {
        assertThrows(IllegalArgumentException.class, () -> client.query(APP, " "));
        assertThrows(IllegalArgumentException.class, () -> client.query(APP, null));
        assertTrue("校验失败不应触达内核", stub.callCount() == 0);
    }
}
