/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.email.MailClient;
import cn.hg.aifei.email.internal.DefaultMailClientProxy;
import cn.hg.aifei.email.internal.MailChannelConfig;
import cn.hg.aifei.email.internal.MailClients;
import cn.hg.aifei.email.internal.MailErrors;
import cn.hg.aifei.email.internal.MailProviderRegistry;
import cn.hg.aifei.email.internal.smtp.MailDeps;
import cn.hg.aifei.email.spi.MailProvider;
import java.util.List;

/**
 * 邮件能力装配入口（对齐短信 / 对象存储插件范式）：构造期只持有配置源引用，解析、校验与装配全部在
 * {@link #start()}；依赖缺失与配置非法均启动即失败，失败即回滚注册表，不留半装配。
 *
 * <p>装配完成后把默认通道的动态委托代理注册为可注入的单例：代理每次调用现取当前默认通道客户端，
 * 故停止后重新装配（配置变更）即时生效；框架未提供注销单例的公开 API，停止时清空本模块注册表与
 * 共享状态，已持有单例经状态校验同样调用失败。</p>
 */
public final class EmailPlugin implements Plugin {

    private static final String PROVIDER_TYPE = "smtp";

    private static volatile boolean singletonRegistered;

    private final Prop prop;
    private volatile boolean started;

    /**
     * @param prop 配置源
     * @throws cn.hg.aifei.email.MailException 配置源为 {@code null}
     */
    public EmailPlugin(Prop prop) {
        if (prop == null) {
            throw MailErrors.propNull();
        }
        this.prop = prop;
    }

    /**
     * 执行装配：依赖探测 → 配置全量解析校验 → 取通道 Provider → 逐通道构建客户端并登记 →
     * 置位装配状态 → 注册注入单例；任一步失败先清空注册表再上抛。
     *
     * @throws cn.hg.aifei.email.MailException 内核依赖缺失、配置非法、通道 Provider 缺失、客户端构建失败
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            MailDeps.requireSms4j();
            List<MailChannelConfig> configs = EmailConfigLoader.load(prop);
            String defaultChannel = EmailConfigLoader.defaultChannel(prop, configs);
            MailProvider provider = MailProviderRegistry.getProvider(PROVIDER_TYPE);
            if (provider == null) {
                throw MailErrors.providerMissing(PROVIDER_TYPE);
            }
            for (MailChannelConfig config : configs) {
                MailClient client = provider.createClient(config.getName(), config.getOptions());
                MailClients.register(config.getName(), client);
            }
            MailClients.registerDefault(defaultChannel);
            MailClients.setStarted(true);
            if (!singletonRegistered) {
                // 框架无注销单例的公开 API 且注册不幂等：生命周期内只注册一次（代理内部不缓存客户端）。
                // 标志为类级静态、方法锁为实例锁，故用类级双检避免多插件实例并发装配时重复注册。
                synchronized (EmailPlugin.class) {
                    if (!singletonRegistered) {
                        AopKit.get().addSingletonObject(MailClient.class, new DefaultMailClientProxy());
                        singletonRegistered = true;
                    }
                }
            }
            started = true;
        } catch (RuntimeException | Error e) {
            MailClients.clear();
            throw e;
        }
    }

    /**
     * 停止装配：清空通道注册表、默认通道与共享装配状态；重复调用无副作用。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        MailClients.clear();
        started = false;
    }
}
