/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.common;

/**
 * 微信接口异常。
 *
 * <p>仅用于传输层失败：网络不可达 / 连接或读超时 / HTTP 状态码非 200 / 响应体非 JSON /
 * {@code WxApiConfig} 未装配。微信业务错误码（errcode != 0）不抛异常，由 {@link ApiResult} 表达。
 */
public class WxApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WxApiException(String message) {
        super(message);
    }

    public WxApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
