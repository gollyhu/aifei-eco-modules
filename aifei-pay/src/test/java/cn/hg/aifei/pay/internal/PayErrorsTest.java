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

import cn.hg.aifei.pay.PayException;
import cn.hg.aifei.pay.support.NoOpLogFactory;
import cn.aifei.log.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 异常收敛与敏感值脱敏：调用方只收到 {@link PayException}，
 * {@code code()} 与 {@code cause} 可见但原始异常类型不进入消息；凭据值在消息中一律 {@code ***}。
 */
public class PayErrorsTest {

    /**
     * 内核错误码：交易不存在。
     */
    private static final String CODE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    /**
     * 内核错误码：系统繁忙。
     */
    private static final String CODE_SYSTEM_ERROR = "ACQ.SYSTEM_ERROR";

    /**
     * 换入前的日志工厂。
     */
    private LogFactory previousLogFactory;

    /**
     * 内核失败路径会记录一次 warn，需要把日志工厂换成空实现。
     */
    @Before
    public void silenceLogs() {
        previousLogFactory = NoOpLogFactory.install();
    }

    /**
     * 还原日志工厂。
     */
    @After
    public void restoreLogs() {
        NoOpLogFactory.restore(previousLogFactory);
    }

    /**
     * 内核异常携带 {@code getCode()} 时，错误码与 cause 都应保留，且消息含动作语义与错误码。
     */
    @Test
    public void shouldExposeCodeAndCauseFromKernelFailure() {
        PayKernelStub.KernelFault fault = new PayKernelStub.KernelFault("业务处理失败", CODE_NOT_EXIST,
                "{\"code\":\"" + CODE_NOT_EXIST + "\",\"msg\":\"交易不存在\"}");

        PayException e = PayErrors.kernelFailure("查单", fault);

        assertEquals("错误码应透出", CODE_NOT_EXIST, e.code());
        assertSame("cause 应为内核原始异常", fault, e.getCause());
        assertTrue("消息应含动作语义：" + e.getMessage(), e.getMessage().contains("查单"));
        assertTrue("消息应含错误码：" + e.getMessage(), e.getMessage().contains(CODE_NOT_EXIST));
        assertTrue("消息应含内核描述：" + e.getMessage(), e.getMessage().contains("交易不存在"));
    }

    /**
     * 内核异常无 {@code getCode()} 时，从响应体 JSON 抽取错误码。
     */
    @Test
    public void shouldExtractCodeFromResponseBody() {
        PayKernelStub.KernelFault fault = new PayKernelStub.KernelFault("接口返回错误", null,
                "{\"code\":\"" + CODE_SYSTEM_ERROR + "\",\"msg\":\"系统繁忙\"}");

        PayException e = PayErrors.kernelFailure("扫码预下单", fault);

        assertEquals("应从响应体抽取错误码", CODE_SYSTEM_ERROR, e.code());
        assertTrue("消息应含内核描述：" + e.getMessage(), e.getMessage().contains("系统繁忙"));
    }

    /**
     * 响应体不可用而消息文本内嵌 JSON 时，仍能抽取错误码（SDK 把响应体拼进 message）。
     */
    @Test
    public void shouldExtractCodeFromExceptionMessage() {
        IllegalStateException fault = new IllegalStateException(
                "com.alipay.v3.ApiException: {\"code\":\"" + CODE_SYSTEM_ERROR + "\",\"msg\":\"系统繁忙\"}");

        PayException e = PayErrors.kernelFailure("退款", fault);

        assertEquals("应从异常消息抽取错误码", CODE_SYSTEM_ERROR, e.code());
    }

    /**
     * 无可提取的错误码时 {@code code()} 为 {@code null}，不编造。
     */
    @Test
    public void shouldLeaveCodeNullWhenNothingToExtract() {
        PayException e = PayErrors.kernelFailure("退款", new IllegalStateException("连接被重置"));

        assertEquals("不应编造错误码", null, e.code());
        assertTrue("消息应含内核消息：" + e.getMessage(), e.getMessage().contains("连接被重置"));
    }

    /**
     * 超长内核消息被截断，避免整段响应体进入异常消息。
     */
    @Test
    public void shouldTruncateOverlongKernelMessage() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longText.append('x');
        }
        PayException e = PayErrors.kernelFailure("查单", new IllegalStateException(longText.toString()));

        assertTrue("消息应被截断：" + e.getMessage().length(), e.getMessage().length() < 600);
        assertTrue("截断后应有省略标记：" + e.getMessage(), e.getMessage().endsWith("..."));
    }

    /**
     * 任何原始异常都被收敛为 {@link PayException}，且消息不含原始异常类名。
     */
    @Test
    public void shouldHideOriginalExceptionTypeInMessage() {
        RuntimeException original = new IllegalStateException("签名失败");

        PayException e = PayErrors.kernelFailure("扫码预下单", original);

        assertTrue("应返回本模块异常类型", e instanceof PayException);
        assertFalse("消息不得含原始类型名：" + e.getMessage(),
                e.getMessage().contains("IllegalStateException"));
        assertSame("cause 仍应保留原始异常", original, e.getCause());
    }

    /**
     * 未装配消息是固定文案，不含键名与凭据。
     */
    @Test
    public void shouldUseFixedMessageForNotInstalled() {
        PayException e = PayErrors.notInstalled();

        assertEquals("支付客户端未装配或已停止", e.getMessage());
        assertEquals("未装配不是内核失败，不应带错误码", null, e.code());
    }

    /**
     * 凭据类键值一律完全不显示（附长度），私钥 / 证书不会经由消息外泄。
     */
    @Test
    public void shouldMaskCredentialValuesCompletely() {
        assertEquals("***（长度 6）", PayMask.mask("pay.apps.2021.private-key", "secret"));
        assertEquals("***（长度 0）", PayMask.mask("pay.apps.2021.private-key", null));
        assertEquals("***（长度 3）", PayMask.mask("pay.apps.2021.app-cert", "abc"));
        assertEquals("***（长度 3）", PayMask.mask("pay.apps.2021.root-cert", "abc"));
        assertEquals("***（长度 3）", PayMask.mask("pay.apps.2021.verify.0.value", "abc"));
    }

    /**
     * 标识类键值原样显示（不构成凭据泄漏），便于定位配置问题。
     */
    @Test
    public void shouldShowIdentifierValues() {
        assertEquals("sandbox", PayMask.mask("pay.apps.2021.env", "sandbox"));
        assertEquals("production", PayMask.mask("pay.apps.2021.env", "production"));
        assertEquals("cert", PayMask.mask("pay.apps.2021.sign-mode", "cert"));
        assertEquals("RSA2", PayMask.mask("pay.apps.2021.sign-type", "RSA2"));
        assertEquals("UTF-8", PayMask.mask("pay.apps.2021.charset", "UTF-8"));
        assertEquals("GET", PayMask.mask("pay.page-http-method", "GET"));
        assertEquals("2021000000000001", PayMask.mask("pay.default-app-id", "2021000000000001"));
    }

    /**
     * 证书序列号只显示前 8 位，便于比对轮换又不泄漏完整值。
     */
    @Test
    public void shouldShowOnlyPrefixOfCertSn() {
        String certSn = "0123456789abcdef0123456789abcdef";

        assertEquals("01234567...", PayMask.mask("pay.apps.2021.verify.0.cert-sn", certSn));
        assertEquals("短值应原样返回", "abc", PayMask.mask("pay.apps.2021.verify.0.cert-sn", "abc"));
        assertEquals("空值返回空串", "", PayMask.mask("pay.apps.2021.verify.0.cert-sn", ""));
    }

    /**
     * 未知键默认保守处理为 {@code ***}。
     */
    @Test
    public void shouldMaskUnknownKeysByDefault() {
        assertEquals("***", PayMask.mask("pay.apps.2021.some-new-key", "value"));
        assertEquals("***", PayMask.mask(null, "value"));
    }

    /**
     * 配置非法消息由「键名 + 脱敏值 + 原因」组成，凭据值不出现。
     */
    @Test
    public void shouldComposeInvalidConfigMessageWithMaskedValue() {
        PayException e = PayErrors.invalidConfig("pay.apps.2021.private-key", "TOPSECRET", "格式非法");

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("pay.apps.2021.private-key"));
        assertTrue("消息应含脱敏占位：" + e.getMessage(), e.getMessage().contains("***（长度 9）"));
        assertFalse("消息不得含凭据明文：" + e.getMessage(), e.getMessage().contains("TOPSECRET"));
    }

    /**
     * 其余消息模板逐条核对（配置缺失 / 未知应用 / 网关不一致 / 验签失败）。
     */
    @Test
    public void shouldComposeDocumentedMessageTemplates() {
        assertTrue(PayErrors.missingConfig("pay.apps.2021.env").getMessage()
                .startsWith("支付宝配置缺失：pay.apps.2021.env"));
        assertTrue(PayErrors.unknownApp("2021999999999999").getMessage()
                .contains("appId=2021999999999999"));
        assertTrue(PayErrors.gatewayEnvMismatch("sandbox", "openapi-sandbox.dl.alipaydev.com",
                "openapi.alipay.com").getMessage().contains("env=sandbox"));
        assertTrue(PayErrors.verifyFailed("2021", "签名校验不通过").getMessage().contains("appId=2021"));
    }

    /**
     * 依赖缺失消息含坐标但不含堆栈信息。
     */
    @Test
    public void shouldComposeMissingDependencyMessageWithoutStackTrace() {
        PayException e = PayErrors.missingDependency("com.github.javen205", "IJPay-AliPay-V3", "3.0.0",
                new ClassNotFoundException("com.ijpay.alipay.v3.AliPayTradeApi"));

        assertTrue("消息应含坐标：" + e.getMessage(), e.getMessage().contains("com.github.javen205"));
        assertFalse("消息不应含堆栈行：" + e.getMessage(), e.getMessage().contains("\tat "));
    }

    /**
     * 真实内核异常的 {@code getCode()} 返回 HTTP 状态码（{@code int}），提取口径必须跳过它并回落到
     * 响应体 JSON 的 {@code code} 字段——否则错误码会被 HTTP 状态码污染。
     */
    @Test
    public void shouldIgnoreNonStringCodeAndFallBackToResponseBody() {
        PayException e = PayErrors.kernelFailure("查单", new HttpStatusFault());

        assertEquals("应跳过 int 形态的 HTTP 状态码，取业务错误码", CODE_NOT_EXIST, e.code());
        assertTrue("消息应含内核描述：" + e.getMessage(), e.getMessage().contains("交易不存在"));
    }

    /**
     * {@code errorCodeOf} 对 {@code null} 与无相关方法的异常保持健壮，内核消息缺失时有占位文案。
     */
    @Test
    public void shouldBeRobustWhenExtractingCodeFromNull() {
        assertEquals("null 原因应返回 null", null, PayErrors.errorCodeOf(null));
        assertEquals("无错误码的异常应返回 null", null, PayErrors.errorCodeOf(new RuntimeException("x")));

        PayException e = PayErrors.kernelFailure("退款", (Throwable) null);

        assertEquals("缺失 cause 时不应编造", null, e.getCause());
        assertTrue("缺失内核消息时应给占位文案：" + e.getMessage(), e.getMessage().contains("内核未提供消息"));
    }

    /**
     * 形态与真实内核异常一致的故障：{@code getCode()} 返回 HTTP 状态码（{@code int}），业务错误码在响应体里。
     */
    private static final class HttpStatusFault extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * @return HTTP 状态码（真实 SDK 的 {@code ApiException#getCode()} 即此形态）
         */
        public int getCode() {
            return 400;
        }

        /**
         * @return 响应体文本
         */
        public String getResponseBody() {
            return "{\"code\":\"" + CODE_NOT_EXIST + "\",\"msg\":\"交易不存在\"}";
        }
    }
}
