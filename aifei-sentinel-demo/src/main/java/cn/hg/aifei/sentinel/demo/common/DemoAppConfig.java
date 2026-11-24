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
import cn.hg.aifei.sentinel.core.SentinelKit;
import cn.hg.aifei.sentinel.demo.aifei.DemoSentinelInterceptor;

/** 装配中心：自写 HIO + Sentinel 拦截器 + 规则加载（两种运行模式由配置文件决定） */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    /** 切换配置文件的系统属性名 */
    public static final String CONFIG_KEY = "demo.config";
    /** 默认配置文件（classpath 根），本地模式 */
    public static final String DEFAULT_CONFIG = "app-config.txt";
    /** 模式判断配置键：是否配置 Dashboard 地址 */
    public static final String DASHBOARD_SERVER_KEY = "sentinel.dashboard.server";
    /** 规则文件配置键 */
    public static final String RULES_FILE_KEY = "sentinel.rules.file";
    /** 规则文件缺省名 */
    public static final String DEFAULT_RULES_FILE = "sentinel-rules.json";

    Prop p;

    /** 装配期持有的配置源，供页面端点读取；与实例字段 p 指向同一对象 */
    private static Prop runtimeProp;

    @Override
    public void config(Settings<In, Out> settings) {
        // 用 -Ddemo.config=app-config-dashboard.txt 切换为 Dashboard 模式
        p = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        runtimeProp = p;
        settings.setLogFactory(new Log4jLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new IoHandler(p.getBoolean("printAction", false), "view"));
    }

    @Override
    public void config(Routes routes) {
        // 限流拦截器放最前：被限流的请求不再走后续拦截器
        routes.scan("cn.hg.aifei.sentinel.demo", new DemoSentinelInterceptor(e -> Out.fail(OutCode.RATE_LIMIT)));
    }

    @Override
    public void config(Plugins plugins) {
        // demo 无数据库 / 缓存插件
    }

    /**
     * 模式判断唯一定义点：是否配置了 Dashboard 地址（空白视为未配置，按本地模式处理）。
     *
     * @param prop 配置源；为 {@code null} 视为本地模式
     * @return 配置了非空 Dashboard 地址返回 {@code true}
     */
    public static boolean isDashboardMode(Prop prop) {
        if (prop == null) {
            return false;
        }
        String server = prop.get(DASHBOARD_SERVER_KEY);
        return server != null && !server.trim().isEmpty();
    }

    /**
     * 页面端点读取装配期配置源。
     *
     * @return 装配期持有的配置源；装配未完成时为 {@code null}
     */
    public static Prop prop() {
        return runtimeProp;
    }

    @Override
    public void onStart() {
        SentinelKit.loadRules(p);
        String mode = isDashboardMode(p) ? "Dashboard" : "本地(local)";
        System.out.println("[aifei-sentinel-demo] Sentinel 模式: " + mode
                + "，规则文件: " + p.get(RULES_FILE_KEY, DEFAULT_RULES_FILE));
    }

    @Override
    public void onStop() {
        SentinelKit.shutdown();
    }
}
