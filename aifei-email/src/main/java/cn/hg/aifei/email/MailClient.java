/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

/**
 * 出站邮件发送门面接口。
 *
 * <p>由装配入口注册为可 {@code @Inject} 的单例（默认通道），命名通道经 {@link MailKit#use(String)} 取用；
 * 实现无可变共享状态，可并发调用，模块不额外加锁。</p>
 */
public interface MailClient {

    /**
     * 同步发送一封邮件：阻塞至 SMTP 返回结果。
     *
     * @param message 邮件消息（收件人 / 主题 / 正文 / 附件）
     * @return 发送结果（成功时 {@code isSuccess} 为 {@code true}）
     * @throws MailException 消息为空、插件未启动或已停止、参数非法、依赖缺失、
     *                       SMTP 返回错误、网络异常、认证失败、附件或模板 IO 失败
     */
    MailResult send(MailMessage message);

    /**
     * 返回本实例绑定的通道标识。
     *
     * @return 通道标识（配置键中的通道名）
     */
    String channelName();
}
