/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.common;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.demo.common.arch.In;
import cn.aifei.demo.common.arch.IoDispatcher;
import cn.aifei.demo.common.arch.IoHandler;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.demo.common.arch.OutCode;
import cn.aifei.log.log4j.Log4jLogFactory;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.Prop;
import cn.aifei.util.PropKit;
import cn.hg.aifei.sentinel.aifei.SentinelInterceptor;
import cn.hg.aifei.sentinel.core.SentinelKit;

/** 装配中心：自写 HIO + Sentinel 拦截器 + 规则加载（两种运行模式由配置文件决定） */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    private static final String CONFIG_KEY = "demo.config";
    private static final String DEFAULT_CONFIG = "app-config.txt";

    Prop p;

    @Override
    public void config(Settings<In, Out> settings) {
        // 用 -Ddemo.config=app-config-dashboard.txt 切换为 Dashboard 模式
        p = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        settings.setLogFactory(new Log4jLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new IoHandler(p.getBoolean("printAction", false), "view"));
    }

    @Override
    public void config(Routes routes) {
        // 限流拦截器放最前：被限流的请求不再走后续拦截器
        routes.scan("cn.hg.aifei.sentinel.demo", new SentinelInterceptor(e -> Out.fail(OutCode.RATE_LIMIT)));
    }

    @Override
    public void config(Plugins plugins) {
        // demo 无数据库 / 缓存插件
    }

    @Override
    public void onStart() {
        SentinelKit.loadRules(p);
        String mode = p.get("sentinel.dashboard.server") == null ? "本地(local)" : "Dashboard";
        System.out.println("[aifei-sentinel-demo] Sentinel 模式: " + mode
                + "，规则文件: " + p.get("sentinel.rules.file", "sentinel-rules.json"));
    }

    @Override
    public void onStop() {
        SentinelKit.shutdown();
    }
}
