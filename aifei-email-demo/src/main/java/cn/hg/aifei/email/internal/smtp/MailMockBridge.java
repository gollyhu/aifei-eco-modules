/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import java.util.function.Function;

/**
 * demo 桥接类：与适配层同包（split package），直接调用包级注入点，**不使用反射**。
 *
 * <p>仅存在于 demo 源码，不进入库 JAR；生产代码不得依赖本类。</p>
 */
public final class MailMockBridge {

    private MailMockBridge() {
    }

    /**
     * 安装内核客户端构建函数（demo 用它把真实发送替换为零外网 mock）。
     *
     * @param factory 构建函数
     */
    public static void installClientFactory(Function<org.dromara.email.comm.config.MailSmtpConfig,
            org.dromara.email.api.MailClient> factory) {
        SmtpClientFactory.setClientFactory(factory);
    }

    /**
     * 以指定类加载器执行依赖探测（demo 用它模拟「未引入内核」）。
     *
     * @param loader 类加载器
     */
    public static void requireSms4j(ClassLoader loader) {
        MailDeps.requireSms4j(loader);
    }
}
