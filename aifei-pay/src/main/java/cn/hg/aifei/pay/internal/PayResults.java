/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.model.PayOrderResult;
import cn.hg.aifei.pay.model.PayQueryResult;
import cn.hg.aifei.pay.model.PayRefundResult;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 内核响应 → 公共值对象的唯一转换点。
 *
 * <p>响应对象由 {@link PayKernel} 以 {@code Object} 交付，本类按「getter 方法名」读取字段，
 * 因此不需要 import 任何内核类型（`docs/design.md` §10 G-04 只允许 4 个 internal 文件出现
 * `com.alipay.*`），同时让内核小版本升级不会波及本转换点（同文档风险 R-6）。若响应是
 * {@code Map}（伪内核的便捷形态），按属性名或下划线键名读取。</p>
 */
public final class PayResults {

    /**
     * getter 前缀。
     */
    private static final String GETTER_PREFIX = "get";

    /**
     * 页面类下单的 HTTP 方法：GET 返回可跳转 URL。
     */
    private static final String PAGE_HTTP_GET = "GET";

    /**
     * 禁止创建工具类实例。
     */
    private PayResults() {
    }

    /**
     * 下单响应 → 下单结果。
     *
     * @param channel        下单渠道
     * @param outTradeNo     商户订单号（请求值，响缺失时回显）
     * @param pageHttpMethod 页面类下单的 HTTP 方法，决定 {@code payloadType}
     * @param response       内核响应：扫码为响应对象，H5 / PC 为载荷字符串
     * @return 下单结果
     */
    public static PayOrderResult toOrderResult(PayOrderResult.Channel channel, String outTradeNo,
                                               String pageHttpMethod, Object response) {
        if (channel == PayOrderResult.Channel.QRCODE) {
            String echo = echo(text(response, "getOutTradeNo"), outTradeNo);
            return PayOrderResult.of(channel, echo, text(response, "getTradeNo"),
                    PayOrderResult.PayloadType.QRCODE, text(response, "getQrCode"));
        }
        PayOrderResult.PayloadType payloadType = PAGE_HTTP_GET.equalsIgnoreCase(pageHttpMethod)
                ? PayOrderResult.PayloadType.URL
                : PayOrderResult.PayloadType.FORM;
        return PayOrderResult.of(channel, echo("", outTradeNo), "", payloadType, text(response, null));
    }

    /**
     * 查单响应 → 查单结果。
     *
     * @param outTradeNo 商户订单号（请求值，响缺失时回显）
     * @param response   内核响应对象
     * @return 查单结果
     */
    public static PayQueryResult toQueryResult(String outTradeNo, Object response) {
        return PayQueryResult.of(
                echo(text(response, "getOutTradeNo"), outTradeNo),
                text(response, "getTradeNo"),
                text(response, "getTradeStatus"),
                text(response, "getTotalAmount"),
                text(response, "getBuyerPayAmount"),
                text(response, "getSendPayDate"));
    }

    /**
     * 退款响应 → 退款结果。
     *
     * @param outTradeNo   商户订单号（请求值，响缺失时回显）
     * @param outRequestNo 退款请求号（幂等键回显）
     * @param response     内核响应对象
     * @return 退款结果
     */
    public static PayRefundResult toRefundResult(String outTradeNo, String outRequestNo, Object response) {
        return PayRefundResult.of(
                echo(text(response, "getOutTradeNo"), outTradeNo),
                text(response, "getTradeNo"),
                echo(text(response, "getOutRequestNo"), outRequestNo),
                text(response, "getRefundFee"),
                text(response, "getFundChange"),
                text(response, "getGmtRefundPay"));
    }

    /**
     * 读取响应字段。
     *
     * @param response 内核响应；字符串形态（页面下单载荷）直接返回其文本
     * @param getter   getter 方法名；{@code null} 表示读取字符串本体
     * @return 字段文本，缺失归一为空串
     */
    private static String text(Object response, String getter) {
        if (response == null) {
            return "";
        }
        if (getter == null) {
            return String.valueOf(response);
        }
        if (response instanceof Map) {
            return mapText((Map<?, ?>) response, property(getter));
        }
        try {
            Method method = response.getClass().getMethod(getter);
            Object value = method.invoke(response);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从 Map 形态的响应中读取字段。
     *
     * @param response 响应 Map
     * @param property 属性名（camelCase）
     * @return 字段文本，缺失归一为空串
     */
    private static String mapText(Map<?, ?> response, String property) {
        Object value = response.get(property);
        if (value == null) {
            value = response.get(snake(property));
        }
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * getter 名 → 属性名。
     *
     * @param getter getter 方法名，如 {@code getQrCode}
     * @return 属性名，如 {@code qrCode}
     */
    private static String property(String getter) {
        String name = getter.startsWith(GETTER_PREFIX) ? getter.substring(GETTER_PREFIX.length()) : getter;
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * camelCase → snake_case。
     *
     * @param property 属性名
     * @return 下划线键名
     */
    private static String snake(String property) {
        StringBuilder builder = new StringBuilder(property.length() + 4);
        for (int i = 0; i < property.length(); i++) {
            char current = property.charAt(i);
            if (Character.isUpperCase(current)) {
                builder.append('_').append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    /**
     * 回显值：优先响应值，缺失时用请求值。
     *
     * @param fromResponse 响应值
     * @param requestValue 请求值
     * @return 非空文本
     */
    private static String echo(String fromResponse, String requestValue) {
        if (fromResponse != null && !fromResponse.isEmpty()) {
            return fromResponse;
        }
        return requestValue == null ? "" : requestValue;
    }
}
