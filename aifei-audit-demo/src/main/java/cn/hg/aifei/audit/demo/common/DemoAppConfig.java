/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.common;

import cn.aifei.aop.AopKit;
import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.demo.common.arch.In;
import cn.aifei.demo.common.arch.IoDispatcher;
import cn.aifei.demo.common.arch.IoHandler;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.log.log4j.Log4jLogFactory;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.Prop;
import cn.aifei.util.PropKit;
import cn.hg.aifei.audit.aifei.AuditInterceptor;
import cn.hg.aifei.audit.aifei.AuditPlugin;
import cn.hg.aifei.audit.demo.actor.SaTokenActorProvider;
import cn.hg.aifei.audit.demo.biz.AuditedOrderService;
import cn.hg.aifei.audit.demo.feed.EventFeed;
import cn.hg.aifei.audit.demo.feed.SinkCounters;
import cn.hg.aifei.audit.demo.mq.AuditMqSubscription;
import cn.hg.aifei.audit.demo.sink.ConsoleAuditSink;
import cn.hg.aifei.audit.demo.sink.MemoryAuditSink;
import cn.hg.aifei.audit.demo.sink.MqAuditSink;
import cn.hg.aifei.audit.demo.sink.SwitchableAuditSink;
import cn.hg.aifei.audit.spi.AuditActorProvider;
import cn.hg.aifei.audit.spi.AuditSink;
import cn.hg.aifei.mq.aifei.MqPlugin;
import cn.hg.aifei.satoken.SaTokenAifeiPlugin;
import cn.hg.aifei.satoken.undertow.SaTokenUndertowDispatcher;
import cn.hg.aifei.trace.TraceKit;
import cn.hg.aifei.trace.web.TraceHandler;

/**
 * 装配中心：自写 HIO + trace / sa-token / mq / audit 四个插件。
 *
 * <p>容器内唯一的 {@link AuditSink} 是「可切换委托 sink」，切换落点只改它的内部委托，业务代码零改动
 * （框架不允许同类型二次注册）。</p>
 *
 * <p>审计拦截器必须第一个传入 {@code routes.scan}（拦截器数组下标 0 即最外层）；
 * {@code service} 包（页面端点）不挂审计拦截器，否则页面自身的查询请求也会产事件、干扰计数。</p>
 *
 * <p>Handler 链：{@code TraceHandler} → {@code IoHandler}。</p>
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    /**
     * 切换配置文件的系统属性名。
     */
    public static final String CONFIG_KEY = "demo.config";

    /**
     * 默认配置文件，位于 classpath 根。
     */
    public static final String DEFAULT_CONFIG = "app-config.txt";

    private static volatile Prop prop;

    @Override
    public void config(Settings<In, Out> settings) {
        prop = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        settings.setLogFactory(new Log4jLogFactory());
        settings.setServer(new UndertowServer(), new SaTokenUndertowDispatcher<In, Out>(new IoDispatcher()))
                .addHandler(new TraceHandler<In, Out>())
                .addHandler(new IoHandler(prop.getBoolean("printAction", false), "view"));

        // 读模型与落点解耦：三个落点都写同一份 feed，页面统一可见
        EventFeed.get();
        SinkCounters.get();
        SwitchableAuditSink sink = SwitchableAuditSink.create(
                new MemoryAuditSink(), new ConsoleAuditSink(), new MqAuditSink());
        AopKit.get().addSingletonObject(AuditSink.class, sink);
        AopKit.get().addSingletonObject(AuditActorProvider.class, new SaTokenActorProvider());
    }

    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.audit.demo.biz", new AuditInterceptor(),
                new AuditedOrderService.ThrownCaptureInterceptor());
        routes.scan("cn.hg.aifei.audit.demo.service");
    }

    @Override
    public void config(Plugins plugins) {
        plugins.add(new MqPlugin(prop));
        plugins.add(new SaTokenAifeiPlugin(prop));
        plugins.add(new AuditPlugin(prop)
                .addScanPackages("cn.hg.aifei.audit.demo.biz")
                .setTraceIdSupplier(TraceKit::getTraceId));
    }

    @Override
    public void onStart() {
        AuditMqSubscription.subscribe();
        System.out.println("[aifei-audit-demo] 装配完成，验证地址: http://localhost:18160/audit/demo/page");
    }

    @Override
    public void onStop() {
        System.out.println("[aifei-audit-demo] 已停止");
    }

    /**
     * @return demo 启动时载入的配置源
     */
    public static Prop prop() {
        return prop;
    }
}
