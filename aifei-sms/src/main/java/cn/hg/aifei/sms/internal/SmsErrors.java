/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal;

import cn.hg.aifei.sms.SmsException;

/**
 * {@link SmsException} 唯一构造点与全部异常消息措辞唯一定义点。
 *
 * <p>门禁锁定 {@code sms: } 前缀仅出现在本类；各实现类只调用本类工厂方法，不自拼消息、不泄漏第三方类型名。</p>
 */
public final class SmsErrors {

    private static final String EMPTY = "<empty>";

    private SmsErrors() {
    }

    /** 手机号为空白。 */
    public static SmsException blankPhoneNumber() {
        return new SmsException("sms: phoneNumber must not be blank");
    }

    /** 模板 ID 为空白。 */
    public static SmsException blankTemplateId() {
        return new SmsException("sms: templateId must not be blank");
    }

    /** 配置项缺失（消息含完整键名与原值）。 */
    public static SmsException requiredConfig(String fullKey, String rawValue) {
        return new SmsException("sms: config " + fullKey + " is required but was: " + display(rawValue));
    }

    /** 通道类型未知。 */
    public static SmsException unknownChannelType(String fullKey, String rawValue) {
        return new SmsException("sms: unknown channel type for " + fullKey + ": " + display(rawValue));
    }

    /** 插件未启动或已停止。 */
    public static SmsException notStarted() {
        return new SmsException("sms: plugin not started or stopped, call SmsPlugin.start() first");
    }

    /** 命名通道不存在。 */
    public static SmsException channelNotFound(String name) {
        return new SmsException("sms: channel not found: " + display(name));
    }

    /** 默认通道配置非法或缺失。 */
    public static SmsException invalidDefaultChannel(String rawValue) {
        return new SmsException("sms: default-channel " + display(rawValue) + " is invalid or missing");
    }

    /** sms4j 依赖缺失。 */
    public static SmsException sms4jMissing() {
        return new SmsException("sms: org.dromara.sms4j:sms4j-core:3.3.6 is required,"
                + " add it to your dependencies");
    }

    /** 厂商返回错误应答（保留 cause，可为 null）。 */
    public static SmsException supplierError(String channel, String code, String vendorMessage, Throwable cause) {
        String msg = "sms: supplier error on channel " + channel + ", code: " + display(code)
                + ", message: " + display(vendorMessage);
        return cause == null ? new SmsException(msg) : new SmsException(msg, cause);
    }

    /** 发送底层网络异常（保留 cause）。 */
    public static SmsException networkError(String channel, Throwable cause) {
        return new SmsException("sms: network error on channel " + channel + ", cause: " + causeMessage(cause), cause);
    }

    /** 回执验签失败。 */
    public static SmsException verificationFailed(String channel) {
        return new SmsException("sms: receipt signature verification failed on channel " + channel);
    }

    /** 回执报文解析失败（保留 cause）。 */
    public static SmsException parseFailed(String channel, Throwable cause) {
        return new SmsException("sms: receipt parse failed on channel " + channel
                + ", cause: " + causeMessage(cause), cause);
    }

    /** 装配入口 Prop 为空。 */
    public static SmsException propNull() {
        return new SmsException("sms: prop can not be null");
    }

    private static String display(String value) {
        return value == null || value.trim().isEmpty() ? EMPTY : value;
    }

    private static String causeMessage(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().trim().isEmpty()) {
            return EMPTY;
        }
        return cause.getMessage();
    }
}
