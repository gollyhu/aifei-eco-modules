/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.MailException;
import cn.hg.aifei.email.MailMessage;
import cn.hg.aifei.email.MailResult;
import cn.hg.aifei.email.internal.MailClients;
import cn.hg.aifei.email.internal.MailErrors;
import java.io.IOException;
import javax.mail.MessagingException;

/**
 * {@link MailClient} 的发送实现（适配层）：参数校验 → 装配状态校验 → 消息映射 → 调用内核发送 →
 * 结果与异常翻译，并在 {@code finally} 按本次映射的临时文件清单清理。
 *
 * <p>无可变字段，可并发调用；内核客户端的线程安全由内核保证，模块不额外加锁。</p>
 */
public final class SmtpMailSender implements MailClient {

    private static final String SUCCESS_MESSAGE = "sent";
    private static final int MAX_CAUSE_DEPTH = 16;

    private final String channelName;
    private final org.dromara.email.api.MailClient delegate;

    /**
     * @param channelName 通道标识
     * @param delegate    内核发送实例
     */
    public SmtpMailSender(String channelName, org.dromara.email.api.MailClient delegate) {
        this.channelName = channelName;
        this.delegate = delegate;
    }

    /**
     * {@inheritDoc}
     *
     * @throws MailException 消息为空、HTML 内容与模板互斥、插件未启动或已停止、附件 IO 失败、
     *                       模板不存在、SMTP 返回错误、网络异常
     */
    @Override
    public MailResult send(MailMessage message) {
        if (message == null) {
            throw MailErrors.messageNull();
        }
        if (notBlank(message.getHtmlContent()) && notBlank(message.getHtmlTemplate())) {
            throw MailErrors.mutuallyExclusiveHtml();
        }
        if (!MailClients.isStarted()) {
            throw MailErrors.notStarted();
        }
        MappedMessage mapped = null;
        try {
            mapped = SmtpMappers.toSms4jMessage(message);
            delegate.send(mapped.getMessage());
            return new MailResult(channelName, true, SUCCESS_MESSAGE);
        } catch (RuntimeException e) {
            if (e instanceof MailException) {
                throw e;
            }
            throw translate(e);
        } finally {
            if (mapped != null) {
                SmtpMappers.cleanupTempFiles(mapped.getTempFiles());
            }
        }
    }

    @Override
    public String channelName() {
        return channelName;
    }

    /**
     * 按异常链分类：链上任一层为 {@link IOException}（含套接字异常 / 连接异常 / 超时异常）判为网络
     * 错误，否则判为 SMTP 返回错误；无法归类时按 SMTP 错误处理。
     *
     * @param cause 内核抛出的运行时异常
     * @return 模块统一异常（保留原因为 cause）
     */
    private MailException translate(RuntimeException cause) {
        if (hasNetworkCause(cause, 0)) {
            return MailErrors.networkError(channelName, cause);
        }
        return MailErrors.smtpError(channelName, cause);
    }

    /**
     * 判定异常链中是否存在网络类异常。
     *
     * <p>实现同时遍历 {@code getCause()} 链与 {@link MessagingException#getNextException()} 链。
     * <b>事实前提</b>：{@code javax.mail 1.6.2} 的 {@code MessagingException#getCause()} 已被重写为
     * 返回内部字段 {@code next}，故 cause 链天然覆盖 next 链；此处显式遍历 {@code getNextException()}
     * 为<b>防御性等价实现</b>（对未重写 {@code getCause()} 的实现或未来版本仍有意义），
     * 不改变分类结果。深度上限用于防御异常链环。</p>
     *
     * @param error 起始异常
     * @param depth 当前深度
     * @return 是否存在网络类异常
     */
    private boolean hasNetworkCause(Throwable error, int depth) {
        if (error == null || depth > MAX_CAUSE_DEPTH) {
            return false;
        }
        if (error instanceof IOException) {
            return true;
        }
        if (error instanceof MessagingException) {
            MessagingException messaging = (MessagingException) error;
            if (hasNetworkCause(messaging.getNextException(), depth + 1)) {
                return true;
            }
        }
        return hasNetworkCause(error.getCause(), depth + 1);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
