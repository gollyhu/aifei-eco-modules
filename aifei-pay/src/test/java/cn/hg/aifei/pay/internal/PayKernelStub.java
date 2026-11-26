/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 伪内核：记录每次调用的应用配置与报文参数，可预设返回值或抛出的异常。
 *
 * <p>必须位于 {@code internal} 包内——{@link PayKernel} 是包级可见的接口，包外无法实现它（这是刻意的：
 * 内核边界不是扩展点）。因此本夹具不是 {@code support} 包的公共件，而是同包可见的测试替身。</p>
 */
final class PayKernelStub implements PayKernel {

    /**
     * 扫码预下单的默认响应。
     */
    private static final Map<String, Object> DEFAULT_PRECREATE_RESPONSE = response(
            "outTradeNo", "T2026010100001", "qrCode", "QR-CONTENT-T2026010100001", "tradeNo", "");

    /**
     * 页面下单的默认载荷（自提交表单）。
     */
    private static final String DEFAULT_PAGE_PAYLOAD =
            "<form name=\"punchout_form\" method=\"post\"></form>";

    /**
     * 查单的默认响应。
     */
    private static final Map<String, Object> DEFAULT_QUERY_RESPONSE = response(
            "outTradeNo", "T2026010100001", "tradeNo", "2026010122001445101000000001",
            "tradeStatus", "TRADE_SUCCESS", "totalAmount", "0.01",
            "buyerPayAmount", "0.01", "sendPayDate", "2026-11-26 10:00:00");

    /**
     * 退款的默认响应。
     */
    private static final Map<String, Object> DEFAULT_REFUND_RESPONSE = response(
            "outTradeNo", "T2026010100001", "tradeNo", "2026010122001445101000000001",
            "outRequestNo", "R2026010100001", "refundFee", "0.01", "fundChange", "Y",
            "gmtRefundPay", "2026-11-26 10:05:00");

    private final List<Call> calls = new ArrayList<>();

    private Object precreateResponse = DEFAULT_PRECREATE_RESPONSE;

    private String pagePayload = DEFAULT_PAGE_PAYLOAD;

    private Object queryResponse = DEFAULT_QUERY_RESPONSE;

    private Object refundResponse = DEFAULT_REFUND_RESPONSE;

    private Exception failure;

    @Override
    public Object precreate(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        record("precreate", app, null, null, bizParams);
        throwIfFailed();
        return precreateResponse;
    }

    @Override
    public String pagePay(PayAppConfig app, String method, String httpMethod, Map<String, Object> bizParams)
            throws Exception {
        record("pagePay", app, method, httpMethod, bizParams);
        throwIfFailed();
        return pagePayload;
    }

    @Override
    public Object query(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        record("query", app, null, null, bizParams);
        throwIfFailed();
        return queryResponse;
    }

    @Override
    public Object refund(PayAppConfig app, Map<String, Object> bizParams) throws Exception {
        record("refund", app, null, null, bizParams);
        throwIfFailed();
        return refundResponse;
    }

    /**
     * 预设全部动作都抛出的异常。
     *
     * @param preset 待抛出的异常，{@code null} 表示不抛
     * @return 入参本身，便于链式使用
     */
    PayKernelStub failingWith(Exception preset) {
        this.failure = preset;
        return this;
    }

    /**
     * 预设扫码预下单向导的响应对象。
     *
     * @param response 响应（可为 {@code Map} 形态）
     * @return 入参本身，便于链式使用
     */
    PayKernelStub precreateResponse(Object response) {
        this.precreateResponse = response;
        return this;
    }

    /**
     * 预设页面下单的载荷。
     *
     * @param payload 载荷
     * @return 入参本身，便于链式使用
     */
    PayKernelStub pagePayload(String payload) {
        this.pagePayload = payload;
        return this;
    }

    /**
     * 预设查单的响应对象。
     *
     * @param response 响应
     * @return 入参本身，便于链式使用
     */
    PayKernelStub queryResponse(Object response) {
        this.queryResponse = response;
        return this;
    }

    /**
     * 预设退款的响应对象。
     *
     * @param response 响应
     * @return 入参本身，便于链式使用
     */
    PayKernelStub refundResponse(Object response) {
        this.refundResponse = response;
        return this;
    }

    /**
     * @return 全部调用记录（按发生顺序，只读）
     */
    List<Call> calls() {
        return Collections.unmodifiableList(calls);
    }

    /**
     * @return 最近一次调用记录
     * @throws IllegalStateException 尚未发生任何调用
     */
    Call lastCall() {
        if (calls.isEmpty()) {
            throw new IllegalStateException("伪内核尚未被调用");
        }
        return calls.get(calls.size() - 1);
    }

    /**
     * @return 调用次数
     */
    int callCount() {
        return calls.size();
    }

    /**
     * 记录一次调用。
     *
     * @param action     动作名
     * @param app        应用配置
     * @param method     接口方法名（页面下单）
     * @param httpMethod HTTP 方法（页面下单）
     * @param bizParams  报文参数
     */
    private void record(String action, PayAppConfig app, String method, String httpMethod,
                        Map<String, Object> bizParams) {
        calls.add(new Call(action, app, method, httpMethod, bizParams));
    }

    /**
     * 按预设抛出异常。
     *
     * @throws Exception 预设的异常
     */
    private void throwIfFailed() throws Exception {
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * 构建字符串键值 Map。
     *
     * @param keyValues 交替出现的键与值
     * @return 只读 Map
     */
    private static Map<String, Object> response(String... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * 一次内核调用的记录。
     */
    static final class Call {

        private final String action;

        private final PayAppConfig app;

        private final String method;

        private final String httpMethod;

        private final Map<String, Object> params;

        /**
         * @param action     动作名
         * @param app        应用配置
         * @param method     接口方法名
         * @param httpMethod HTTP 方法
         * @param params     报文参数
         */
        Call(String action, PayAppConfig app, String method, String httpMethod, Map<String, Object> params) {
            this.action = action;
            this.app = app;
            this.method = method;
            this.httpMethod = httpMethod;
            this.params = params == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(params);
        }

        /**
         * @return 动作名
         */
        String action() {
            return action;
        }

        /**
         * @return 收到的应用配置
         */
        PayAppConfig app() {
            return app;
        }

        /**
         * @return 页面下单的接口方法名，非页面下单为 {@code null}
         */
        String method() {
            return method;
        }

        /**
         * @return 页面下单的 HTTP 方法，非页面下单为 {@code null}
         */
        String httpMethod() {
            return httpMethod;
        }

        /**
         * @return 报文参数副本
         */
        Map<String, Object> params() {
            return params;
        }

        /**
         * 取报文字段。
         *
         * @param key 键名
         * @return 字段值；缺失为 {@code null}
         */
        String param(String key) {
            Object value = params.get(key);
            return value == null ? null : value.toString();
        }

        /**
         * 取 {@code biz_content} 业务参数容器（页面下单把业务参数放在其中）。
         *
         * @return 业务参数字段 Map；不存在时为空 Map
         */
        @SuppressWarnings("unchecked")
        Map<String, Object> bizContent() {
            Object value = params.get("biz_content");
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            return Collections.emptyMap();
        }

        /**
         * 取 {@code biz_content} 中的文本字段。
         *
         * @param key 键名
         * @return 字段值；缺失为 {@code null}
         */
        String bizContentParam(String key) {
            Object value = bizContent().get(key);
            return value == null ? null : value.toString();
        }
    }

    /**
     * 伪内核故障：携带业务错误码与响应体，形态与内核异常一致（{@code getCode()} / {@code getResponseBody()}）。
     */
    static final class KernelFault extends Exception {

        private static final long serialVersionUID = 1L;

        private final String code;

        private final String responseBody;

        /**
         * @param message      异常消息
         * @param code         业务错误码
         * @param responseBody 响应体文本，可为 {@code null}
         */
        KernelFault(String message, String code, String responseBody) {
            super(message);
            this.code = code;
            this.responseBody = responseBody;
        }

        /**
         * @return 业务错误码
         */
        public String getCode() {
            return code;
        }

        /**
         * @return 响应体文本
         */
        public String getResponseBody() {
            return responseBody;
        }
    }
}
