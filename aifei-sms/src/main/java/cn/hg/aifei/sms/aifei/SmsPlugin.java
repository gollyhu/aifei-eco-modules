/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.sms.SmsClient;
import cn.hg.aifei.sms.internal.SmsChannelConfig;
import cn.hg.aifei.sms.internal.SmsClients;
import cn.hg.aifei.sms.internal.SmsErrors;
import cn.hg.aifei.sms.internal.sms4j.SmsBlendFactory;
import cn.hg.aifei.sms.internal.sms4j.SmsDeps;
import java.util.List;
import java.util.Map;

/**
 * 短信能力装配入口（对齐 {@code OssPlugin} 等范式）：构造期只持有 {@link Prop} 引用，
 * 解析、校验与装配全部在 {@link #start()}；依赖缺失与配置非法均启动即失败，失败即回滚。
 *
 * <p>装配完成后把默认通道客户端注册为可 {@code @Inject} 的单例；框架未提供注销单例的公开 API，
 * 故 {@link #stop()} 清空本模块注册表与共享状态，已持有单例经状态校验同样调用失败。</p>
 */
public final class SmsPlugin implements Plugin {

    private static volatile boolean singletonRegistered;

    private final Prop prop;
    private volatile boolean started;

    /**
     * @param prop 配置源
     * @throws cn.hg.aifei.sms.SmsException {@code prop} 为 {@code null}
     */
    public SmsPlugin(Prop prop) {
        if (prop == null) {
            throw SmsErrors.propNull();
        }
        this.prop = prop;
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        try {
            SmsDeps.requireSms4j();
            List<SmsChannelConfig> configs = SmsConfigLoader.load(prop);
            String defaultChannel = SmsConfigLoader.defaultChannel(prop, configs);
            Map<String, SmsClient> clients = SmsBlendFactory.buildClients(configs);
            for (SmsChannelConfig config : configs) {
                SmsClients.register(config.getName(), clients.get(config.getName()), config);
            }
            SmsClients.registerDefault(defaultChannel);
            SmsClients.setStarted(true);
            if (!singletonRegistered) {
                // 框架无注销单例的公开 API 且 addSingletonObject 不幂等：生命周期内只注册一次
                AopKit.get().addSingletonObject(SmsClient.class, SmsClients.defaultClient());
                singletonRegistered = true;
            }
            started = true;
        } catch (RuntimeException | Error e) {
            SmsClients.clear();
            throw e;
        }
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        SmsClients.clear();
        started = false;
    }
}
