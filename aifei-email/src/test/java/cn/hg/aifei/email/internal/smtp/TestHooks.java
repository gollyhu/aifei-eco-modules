/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal.smtp;

import java.util.function.Function;

/**
 * 测试用桥接类（仅存在于测试源码，不进主 JAR、不参与门禁计数）：把适配层的包级注入点暴露给根测试包。
 *
 * <p>生产代码不得依赖本类；其存在只是为了让根包测试无需反射即可安装模拟客户端与模拟依赖探测。</p>
 */
public final class TestHooks {

    private TestHooks() {
    }

    /**
     * 安装客户端构建函数。
     *
     * @param factory 构建函数（{@code null} 等同恢复默认）
     */
    public static void installClientFactory(Function<org.dromara.email.comm.config.MailSmtpConfig,
            org.dromara.email.api.MailClient> factory) {
        SmtpClientFactory.setClientFactory(factory);
    }

    /**
     * 恢复默认真实构建函数。
     */
    public static void resetClientFactory() {
        SmtpClientFactory.setClientFactory(null);
    }

    /**
     * 以指定类加载器执行依赖探测（用于模拟依赖缺失）。
     *
     * @param loader 类加载器
     */
    public static void requireSms4j(ClassLoader loader) {
        MailDeps.requireSms4j(loader);
    }
}
