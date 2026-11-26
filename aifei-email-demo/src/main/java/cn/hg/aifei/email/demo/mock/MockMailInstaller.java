/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo.mock;

import cn.hg.aifei.email.internal.smtp.MailMockBridge;
import java.util.function.Function;

/**
 * 安装 mock 内核客户端：经同包桥接调用适配层注入点（D11 / §7.6），使 demo 全程零外网。
 *
 * <p>必须在 {@code EmailPlugin.start()} 之前调用（装配期按通道构建客户端时生效）。</p>
 */
public final class MockMailInstaller {

    private MockMailInstaller() {
    }

    /**
     * 安装「每个通道一个 mock 客户端」的构建函数。
     */
    public static void install() {
        MailMockBridge.installClientFactory(new Function<org.dromara.email.comm.config.MailSmtpConfig,
                org.dromara.email.api.MailClient>() {
            @Override
            public org.dromara.email.api.MailClient apply(org.dromara.email.comm.config.MailSmtpConfig config) {
                return new MockSms4jMailClient(config.getSmtpServer());
            }
        });
        System.out.println("[aifei-email-demo] 已安装 mock 内核客户端：零外网、零真实邮件");
    }
}
