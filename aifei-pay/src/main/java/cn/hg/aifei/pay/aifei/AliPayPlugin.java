/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.pay.AliPayClient;
import cn.hg.aifei.pay.internal.DefaultAliPayClient;
import cn.hg.aifei.pay.internal.PayClients;
import cn.hg.aifei.pay.internal.PayConfig;
import cn.hg.aifei.pay.internal.PayDeps;
import cn.hg.aifei.pay.internal.PayHolder;
import cn.hg.aifei.pay.internal.PayVerifier;

/**
 * 支付宝接入的装配入口：解析 {@code pay.*} → 探测依赖 → 预建客户端 → 注册 {@code @Inject} 单例 →
 * 写入门面实例。
 *
 * <p>构造期只持配置源引用，不解析、不校验、不建客户端。启动失败**不留半装配态**：配置非法或容器注册
 * 失败时门面保持未装配。停止只清门面——框架无注销 API，已注入字段仍持旧实例属已知边界
 * （与出站 HTTP 模块同口径，README 已声明）。</p>
 */
public class AliPayPlugin implements Plugin {

    private final Prop prop;

    /**
     * 首次装配建成的客户端；再次启动复用它，避免重复向容器注册。
     */
    private DefaultAliPayClient client;

    /**
     * 首次装配建成的验签器；再次启动复用它。
     */
    private PayVerifier verifier;

    private volatile boolean started;

    /**
     * @param prop 配置源
     * @throws IllegalArgumentException {@code prop} 为 {@code null}
     */
    public AliPayPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop 不能为 null");
        }
        this.prop = prop;
    }

    /**
     * 装配交易客户端与验签器，并登记为 {@code @Inject} 单例；重复启动不作任何事。
     *
     * @throws cn.hg.aifei.pay.AliPayException 配置非法、内核依赖缺失或证书模式缺 BouncyCastle
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        PayConfig config = PayConfig.parse(prop.getProperties());
        PayDeps.requireKernel();
        if (config.certMode()) {
            PayDeps.requireBouncyCastle();
        }
        if (client == null) {
            PayClients clients = new PayClients(config);
            clients.preload();
            DefaultAliPayClient created = DefaultAliPayClient.create(config, clients);
            PayVerifier createdVerifier = new PayVerifier(config.apps());
            PayHolder.install(created, createdVerifier);
            try {
                AopKit.get().addSingletonObject(AliPayClient.class, created);
            } catch (RuntimeException e) {
                PayHolder.clear();
                throw e;
            } catch (Error e) {
                PayHolder.clear();
                throw e;
            }
            this.client = created;
            this.verifier = createdVerifier;
        } else {
            PayHolder.install(client, verifier);
        }
        this.started = true;
    }

    /**
     * 清除门面实例；重复停止不作任何事。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        PayHolder.clear();
        this.started = false;
    }
}
