/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

import cn.hg.aifei.email.internal.MailClients;
import java.util.Set;

/**
 * 邮件静态门面（有状态 Kit）：<b>须先完成 {@code EmailPlugin.start()} 装配</b>方可调用。
 *
 * <p>未装配或已停止时调用抛 {@link MailException}（插件未启动或已停止）；本类只做委托，
 * 不持有客户端实例、不缓存状态。</p>
 */
public final class MailKit {

    private MailKit() {
    }

    /**
     * 取默认通道客户端（由默认通道配置项指定，单通道时取唯一通道）。
     *
     * @return 默认通道客户端
     * @throws MailException 插件未启动或已停止
     */
    public static MailClient defaultClient() {
        return MailClients.defaultClient();
    }

    /**
     * 按通道标识取命名通道客户端。
     *
     * @param channelName 通道标识（配置键 {@code email.<name>.*} 中的 {@code <name>}）
     * @return 命名通道客户端
     * @throws MailException 插件未启动或已停止、通道不存在
     */
    public static MailClient use(String channelName) {
        return MailClients.use(channelName);
    }

    /**
     * 返回已装配的通道标识集合（不可变）。
     *
     * @return 通道标识集合
     * @throws MailException 插件未启动或已停止
     */
    public static Set<String> channels() {
        return MailClients.channels();
    }

    /**
     * 便捷发送，等价于 {@code defaultClient().send(message)}。
     *
     * @param message 邮件消息
     * @return 发送结果
     * @throws MailException 插件未启动或已停止、参数非法、依赖缺失、SMTP 错误、网络异常、IO 失败
     */
    public static MailResult send(MailMessage message) {
        return defaultClient().send(message);
    }
}
