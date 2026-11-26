/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.MailException;
import cn.hg.aifei.email.spi.MailProvider;
import java.util.Map;

/**
 * SMTP 通道的 {@link MailProvider} 实现（首期唯一实现，经 {@code META-INF/services} 发现）。
 *
 * <p>类签名不含内核类型，内核引用只出现在方法体委托的装配点中，故依赖缺失时本类仍可被
 * {@link java.util.ServiceLoader} 正常加载，不会触发服务配置错误。</p>
 */
public final class SmtpMailProvider implements MailProvider {

    private static final String TYPE = "smtp";

    @Override
    public String type() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @throws MailException 通道配置无法构建客户端
     */
    @Override
    public MailClient createClient(String channelName, Map<String, String> options) {
        return SmtpClientFactory.create(channelName, options);
    }
}
