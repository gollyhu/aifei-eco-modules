/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.MailException;
import cn.hg.aifei.email.internal.MailErrors;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * 客户端构建唯一装配点（设计 §7.1 / D11）：原始配置项 → 内核配置对象 → 内核客户端 → 模块发送实现。
 *
 * <p>默认经内核编程式构建 API 生成客户端（无静态注册表副作用，停止时无需清理内核全局状态）；
 * 包级注入点 {@link #setClientFactory(Function)} 供测试与示例工程注入模拟客户端，不进入公共面。
 * 本类返回模块 {@link MailClient}，使内核客户端类型不出适配层。</p>
 */
public final class SmtpClientFactory {

    private static final String KEY_SMTP_SERVER = "smtp-server";
    private static final String KEY_PORT = "port";
    private static final String KEY_FROM_ADDRESS = "from-address";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_IS_SSL = "is-ssl";
    private static final String KEY_IS_AUTH = "is-auth";
    private static final String KEY_NICK_NAME = "nick-name";
    private static final String KEY_RETRY_INTERVAL = "retry-interval";
    private static final String KEY_MAX_RETRIES = "max-retries";

    private static final String DEFAULT_TRUE = "true";
    private static final int DEFAULT_RETRY_INTERVAL = 5;
    private static final int DEFAULT_MAX_RETRIES = 0;

    private static final Function<org.dromara.email.comm.config.MailSmtpConfig,
            org.dromara.email.api.MailClient> DEFAULT_FACTORY =
            new Function<org.dromara.email.comm.config.MailSmtpConfig, org.dromara.email.api.MailClient>() {
                @Override
                public org.dromara.email.api.MailClient apply(org.dromara.email.comm.config.MailSmtpConfig config) {
                    try {
                        return org.dromara.email.core.service.MailBuild.build(config);
                    } catch (javax.mail.MessagingException e) {
                        throw new org.dromara.email.comm.errors.MailException(e);
                    }
                }
            };

    private static volatile Function<org.dromara.email.comm.config.MailSmtpConfig,
            org.dromara.email.api.MailClient> clientFactory = DEFAULT_FACTORY;

    private SmtpClientFactory() {
    }

    /**
     * 替换客户端构建函数；传 {@code null} 恢复默认真实构建。
     *
     * @param factory 构建函数（包级注入点，仅同包测试 / 反射桥接调用）
     */
    static void setClientFactory(Function<org.dromara.email.comm.config.MailSmtpConfig,
            org.dromara.email.api.MailClient> factory) {
        clientFactory = factory == null ? DEFAULT_FACTORY : factory;
    }

    /**
     * 按通道配置项构建该通道的发送客户端。
     *
     * @param channelName 通道标识
     * @param options     原始配置项（kebab-case 键名到值；未知键忽略；缺省键按默认值填充）
     * @return 该通道的模块发送客户端
     * @throws MailException 整数配置项格式非法、客户端构建失败
     */
    public static MailClient create(String channelName, Map<String, String> options) {
        Map<String, String> values = options == null
                ? Collections.<String, String>emptyMap() : options;
        org.dromara.email.comm.config.MailSmtpConfig config =
                org.dromara.email.comm.config.MailSmtpConfig.builder()
                        .smtpServer(values.get(KEY_SMTP_SERVER))
                        .port(values.get(KEY_PORT))
                        .fromAddress(values.get(KEY_FROM_ADDRESS))
                        .username(values.get(KEY_USERNAME))
                        .password(values.get(KEY_PASSWORD))
                        .nickName(values.get(KEY_NICK_NAME))
                        .isSSL(valueOrDefault(values.get(KEY_IS_SSL), DEFAULT_TRUE))
                        .isAuth(valueOrDefault(values.get(KEY_IS_AUTH), DEFAULT_TRUE))
                        .retryInterval(intValue(values, KEY_RETRY_INTERVAL, DEFAULT_RETRY_INTERVAL))
                        .maxRetries(intValue(values, KEY_MAX_RETRIES, DEFAULT_MAX_RETRIES))
                        .build();
        org.dromara.email.api.MailClient delegate;
        try {
            delegate = clientFactory.apply(config);
        } catch (RuntimeException e) {
            throw MailErrors.buildClientFailed(channelName, rootCause(e));
        }
        return new SmtpMailSender(channelName, delegate);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private static int intValue(Map<String, String> values, String key, int defaultValue) {
        String raw = values.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            // 装配期已在配置解析层校验并报出完整键名；此处为二次防线
            throw MailErrors.invalidInteger(key, raw);
        }
    }

    private static Throwable rootCause(RuntimeException cause) {
        Throwable current = cause.getCause();
        return current == null ? cause : current;
    }
}
