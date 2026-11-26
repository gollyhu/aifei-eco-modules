/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo.mock;

import java.util.List;
import java.util.Map;

/**
 * demo 用内核发送客户端 mock：{@code send} 不抛异常即模拟成功，仅打印一行摘要。
 *
 * <p>绝不建立 SMTP 连接、绝不外发邮件，保证 demo 零外网。</p>
 */
public final class MockSms4jMailClient implements org.dromara.email.api.MailClient {

    private final String smtpServer;

    /**
     * @param smtpServer 该通道的 SMTP 服务器地址（仅用于日志展示）
     */
    public MockSms4jMailClient(String smtpServer) {
        this.smtpServer = smtpServer;
    }

    /**
     * 记录一封邮件的关键字段后返回（无异常即成功）。
     *
     * @param message 内核消息
     */
    @Override
    public void send(org.dromara.email.comm.entity.MailMessage message) {
        System.out.println("[mock-email] " + smtpServer
                + " accepted to=" + message.getMailAddress()
                + ", subject=" + message.getTitle()
                + ", html=" + htmlKind(message)
                + ", variables=" + sizeOf(message.getHtmlValues())
                + ", attachments=" + sizeOf(message.getFiles()));
    }

    private static String htmlKind(org.dromara.email.comm.entity.MailMessage message) {
        if (message.getHtmlInputStream() != null) {
            return "template";
        }
        if (message.getHtmlContent() != null) {
            return "content";
        }
        return "none";
    }

    private static int sizeOf(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private static int sizeOf(Map<String, ?> values) {
        return values == null ? 0 : values.size();
    }
}
