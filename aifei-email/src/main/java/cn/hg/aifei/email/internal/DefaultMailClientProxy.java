/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal;

import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.MailException;
import cn.hg.aifei.email.MailMessage;
import cn.hg.aifei.email.MailResult;

/**
 * 注入用动态委托代理（非承诺实现层构件）：实现 {@link MailClient} 但不缓存具体客户端。
 *
 * <p>每次调用都经 {@link MailClients#defaultClient()} 现取当前注册表的默认通道客户端再委托，
 * 故装配 → 停止 → 重新装配（配置变更）后，注入单例自动指向新装配的发送实例，无需重启应用；
 * 停止后调用同样触发「插件未启动或已停止」失败，不会出现持有旧实例仍发送成功的假象。</p>
 */
public final class DefaultMailClientProxy implements MailClient {

    /**
     * 委托当前默认通道客户端发送。
     *
     * @param message 邮件消息
     * @return 发送结果
     * @throws MailException 插件未启动或已停止、参数非法、SMTP 错误、网络异常、IO 失败
     */
    @Override
    public MailResult send(MailMessage message) {
        return MailClients.defaultClient().send(message);
    }

    /**
     * 委托当前默认通道客户端，返回其通道标识。
     *
     * @return 当前默认通道标识
     * @throws MailException 插件未启动或已停止
     */
    @Override
    public String channelName() {
        return MailClients.defaultClient().channelName();
    }
}
