/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.spi;

import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.MailException;
import java.util.Map;

/**
 * 通道扩展 SPI：为未来接入厂商邮件 HTTP API（SendGrid / SES / Mailgun / 阿里云邮件等）预留扩展路径，
 * 首期唯一实现为 SMTP 通道（经 {@code META-INF/services} 发现）。
 *
 * <p>签名只使用 JDK 类型与本模块公共类型，零第三方、零框架类型；消费方实现后经
 * {@code META-INF/services/cn.hg.aifei.email.spi.MailProvider} 声明即可被装配入口发现。</p>
 */
public interface MailProvider {

    /**
     * 通道类型标识（如 {@code "smtp"}）。
     *
     * @return 通道类型标识
     */
    String type();

    /**
     * 按通道配置创建客户端实例。
     *
     * @param channelName 通道标识（配置键 {@code email.<name>.*} 中的 {@code <name>}）
     * @param options     该通道的原始配置项（kebab-case 键名到值），未经改写的副本
     * @return 该通道的发送客户端
     * @throws MailException 通道配置无法构建客户端
     */
    MailClient createClient(String channelName, Map<String, String> options);
}
