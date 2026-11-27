/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal;

import cn.hg.aifei.email.MailException;

/**
 * {@link MailException} 唯一构造点与全部异常消息措辞唯一定义点。
 *
 * <p>门禁锁定消息前缀只出现在本类；各实现类只调用本类工厂方法，不自拼消息、不泄漏第三方类型名。
 * 消息正文只取被包装原因的 {@code getMessage()}，不含第三方异常类名。</p>
 */
public final class MailErrors {

    private static final String EMPTY = "<empty>";

    private MailErrors() {
    }

    /** 场景 0（装配构件入参）：装配入口的配置源为空。 */
    public static MailException propNull() {
        return new MailException("email: prop can not be null");
    }

    /** 场景 1：发送消息为 {@code null}。 */
    public static MailException messageNull() {
        return new MailException("email: message must not be null");
    }

    /** 场景 2：收件人列表为空。 */
    public static MailException emptyRecipients() {
        return new MailException("email: recipient list must not be empty");
    }

    /** 场景 3：主题为空白。 */
    public static MailException blankSubject() {
        return new MailException("email: subject must not be blank");
    }

    /** 场景 4：未声明通道 / 必填项缺失（消息含完整键名与原值）。 */
    public static MailException requiredConfig(String fullKey, String rawValue) {
        return new MailException("email: config " + fullKey + " is required but was: " + display(rawValue));
    }

    /** 场景 5：插件未启动或已停止。 */
    public static MailException notStarted() {
        return new MailException("email: plugin not started or stopped, call EmailPlugin.start() first");
    }

    /** 场景 6：命名通道不存在。 */
    public static MailException channelNotFound(String name) {
        return new MailException("email: channel not found: " + display(name));
    }

    /** 场景 7：默认通道指向不存在通道 / 多通道时缺失。 */
    public static MailException invalidDefaultChannel(String rawValue) {
        return new MailException("email: default-channel " + display(rawValue) + " is invalid or missing");
    }

    /** 场景 8：sms4j 邮件插件依赖缺失。 */
    public static MailException sms4jMissing() {
        return new MailException("email: org.dromara.sms4j:sms4j-Email-core:3.3.6 is required,"
                + " add it to your dependencies");
    }

    /** 场景 9：整数配置项格式非法（消息含键名与原值）。 */
    public static MailException invalidInteger(String fullKey, String rawValue) {
        return new MailException("email: config " + fullKey + " must be an integer but was: " + display(rawValue));
    }

    /** 场景 10：SMTP 返回错误（认证失败 / 收件人拒绝等，保留 cause）。 */
    public static MailException smtpError(String channel, Throwable cause) {
        return new MailException("email: smtp error on channel " + channel + ", cause: " + causeMessage(cause), cause);
    }

    /** 场景 11：发送底层网络异常（保留 cause）。 */
    public static MailException networkError(String channel, Throwable cause) {
        return new MailException("email: network error on channel " + channel + ", cause: " + causeMessage(cause),
                cause);
    }

    /** 场景 12：HTML 内容串与 HTML 模板名同时非空（互斥校验）。 */
    public static MailException mutuallyExclusiveHtml() {
        return new MailException("email: htmlContent and htmlTemplate are mutually exclusive");
    }

    /** 场景 13：找不到指定类型的通道 Provider。 */
    public static MailException providerMissing(String type) {
        return new MailException("email: no MailProvider found for type " + type
                + ", check META-INF/services/cn.hg.aifei.email.spi.MailProvider");
    }

    /** 场景 14：附件临时文件写入失败（保留 cause）。 */
    public static MailException attachmentWriteFailed(String name, Throwable cause) {
        return new MailException("email: failed to write attachment " + name + " to temp file, cause: "
                + causeMessage(cause), cause);
    }

    /** 场景 15：HTML 模板经 TCCL 从 classpath 加载不到。 */
    public static MailException htmlTemplateNotFound(String name) {
        return new MailException("email: html template not found: " + display(name) + " on classpath template/");
    }

    /** 场景 16：装配期构建 SMTP 客户端失败（保留 cause）。 */
    public static MailException buildClientFailed(String channel, Throwable cause) {
        return new MailException("email: failed to build smtp client for channel " + channel + ", cause: "
                + causeMessage(cause), cause);
    }

    /** 同一通道类型存在多个 Provider 实现（装配期错误）。 */
    public static MailException duplicateProvider(String type) {
        return new MailException("email: multiple MailProvider implementations found for type " + display(type));
    }

    private static String display(String value) {
        return value == null || value.trim().isEmpty() ? EMPTY : value;
    }

    /**
     * 取被包装原因的可读描述：沿 cause 链取最深一层的非空消息。
     *
     * <p>外层包装异常的 {@code getMessage()} 通常是内层异常的 {@code toString()}（含第三方异常类名），
     * 故下沉到最深层，保证消息正文不含第三方类型名。</p>
     *
     * @param cause 被包装原因
     * @return 可读描述，无可用消息时返回占位符
     */
    private static String causeMessage(Throwable cause) {
        String message = null;
        Throwable current = cause;
        while (current != null) {
            String text = current.getMessage();
            if (text != null && !text.trim().isEmpty()) {
                message = text;
            }
            current = current.getCause();
        }
        return message == null ? EMPTY : message;
    }
}
