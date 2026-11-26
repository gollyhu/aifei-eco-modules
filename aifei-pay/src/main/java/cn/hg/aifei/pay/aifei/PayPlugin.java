/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.pay.PayClient;
import cn.hg.aifei.pay.internal.AliPayChannelVerifier;
import cn.hg.aifei.pay.internal.AliPayV2Clients;
import cn.hg.aifei.pay.internal.AliPayV2Verifier;
import cn.hg.aifei.pay.internal.DefaultPayClient;
import cn.hg.aifei.pay.internal.channels.ChannelAdapters;
import cn.hg.aifei.pay.internal.channels.ChannelConfig;
import cn.hg.aifei.pay.internal.channels.ChannelConfigLoader;
import cn.hg.aifei.pay.internal.PayChannelConfigLoader;
import cn.hg.aifei.pay.internal.PayChannelRegistry;
import cn.hg.aifei.pay.internal.PayClients;
import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.internal.PayDeps;
import cn.hg.aifei.pay.internal.PayTypes;
import cn.hg.aifei.pay.internal.PayVerifier;
import cn.hg.aifei.pay.internal.UnimplementedPayClient;
import cn.hg.aifei.pay.internal.UnimplementedPayVerifier;
import java.util.List;
import java.util.Properties;

/**
 * 多通道装配入口：解析 {@code pay.<name>.*} → 逐通道建客户端与验签器 → 注册默认 {@code @Inject} 单例。
 */
public class PayPlugin implements Plugin {

    private final Prop prop;

    private volatile boolean started;

    /**
     * @param prop 配置源
     */
    public PayPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop 不能为 null");
        }
        this.prop = prop;
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        Properties properties = prop.getProperties();
        List<PayChannelConfigLoader.ChannelSpec> specs = PayChannelConfigLoader.load(properties);
        String defaultName = PayChannelConfigLoader.defaultChannel(properties, specs);
        try {
            for (PayChannelConfigLoader.ChannelSpec spec : specs) {
                if (PayTypes.ALIPAY3.equals(spec.type())) {
                    registerAlipay3(spec, properties);
                } else if (PayTypes.ALIPAY2.equals(spec.type())) {
                    registerAlipay2(spec, properties);
                } else if (PayTypes.WXPAY.equals(spec.type())) {
                    PayChannelRegistry.register(spec.name(),
                            new UnimplementedPayClient(spec.name(), spec.type()),
                            new UnimplementedPayVerifier());
                } else {
                    registerChannel(spec, properties);
                }
                if (spec.name().equals(defaultName)) {
                    PayChannelRegistry.registerDefault(spec.name());
                }
            }
            PayChannelRegistry.setStarted(true);
            AopKit.get().addSingletonObject(PayClient.class, PayChannelRegistry.use(defaultName));
            this.started = true;
        } catch (RuntimeException | Error e) {
            PayChannelRegistry.clear();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        PayChannelRegistry.clear();
        this.started = false;
    }

    private static void registerAlipay3(PayChannelConfigLoader.ChannelSpec spec, Properties properties) {
        PayConfig config = parseAlipayConfig(properties, spec);
        PayDeps.requireV3Kernel();
        if (config.certMode()) {
            PayDeps.requireBouncyCastle();
        }
        PayClients clients = new PayClients(config);
        clients.preload();
        PayChannelRegistry.register(spec.name(), DefaultPayClient.create(config, clients, spec.name()),
                new AliPayChannelVerifier(new PayVerifier(config.apps())));
    }

    private static void registerAlipay2(PayChannelConfigLoader.ChannelSpec spec, Properties properties) {
        PayConfig config = parseAlipayConfig(properties, spec);
        PayDeps.requireV2Kernel();
        if (config.certMode()) {
            PayDeps.requireBouncyCastle();
        }
        AliPayV2Clients clients = new AliPayV2Clients(config);
        clients.preload();
        PayChannelRegistry.register(spec.name(), DefaultPayClient.createV2(config, clients, spec.name()),
                new AliPayV2Verifier(config.apps()));
    }

    private static void registerChannel(PayChannelConfigLoader.ChannelSpec spec, Properties properties) {
        ChannelConfig config = ChannelConfigLoader.load(properties, spec.name(), spec.type(), spec.merchantId());
        PayChannelRegistry.register(spec.name(), ChannelAdapters.createClient(config),
                ChannelAdapters.createVerifier(config));
    }

    private static PayConfig parseAlipayConfig(Properties properties, PayChannelConfigLoader.ChannelSpec spec) {
        return PayConfig.parse(PayChannelConfigLoader.legacyAlipayProperties(properties, spec));
    }
}
