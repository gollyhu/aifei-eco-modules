/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.PropKit;
import cn.hg.aifei.i18n.aifei.I18nPlugin;
import cn.hg.aifei.i18n.demo.arch.In;
import cn.hg.aifei.i18n.demo.arch.IoDispatcher;
import cn.hg.aifei.i18n.demo.arch.IoHandler;
import cn.hg.aifei.i18n.demo.arch.Out;
import cn.hg.aifei.i18n.demo.arch.RootRedirectHandler;
import cn.hg.aifei.i18n.demo.console.ConsoleLogFactory;
import cn.hg.aifei.i18n.demo.db.I18nDbSeed;
import cn.hg.aifei.i18n.web.I18nHandler;

/**
 * 装配中心：aifei-i18n 指令与语言包下发的演示入口。
 *
 * <p>{@code config(Plugins)} 注册 {@link I18nPlugin}（解析 i18n.* 并装门面，否则 I18nKit 未装配）；
 * Handler 链首 {@link I18nHandler} 绑定 Accept-Language，次 {@link RootRedirectHandler} 把根路径重定向到主页面，
 * 末 {@link IoHandler} 构造引擎、注册三条指令并在 render 前后清理页面级状态。</p>
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Settings<In, Out> settings) {
        // 先载入配置：I18nPlugin 与 IoHandler 均经 PropKit 读取
        PropKit.use("app-config.txt");
        // aifei 内核默认 NoLogFactory 每次取日志即抛，给一个真实实现；零日志绑定
        settings.setLogFactory(new ConsoleLogFactory());
        // Db 来源在 I18nPlugin.start() 即被实例化，DbKit 必须在此之前就绪
        I18nDbSeed.init();
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new I18nHandler<In, Out>(in -> in.getHeader("Accept-Language")))
                .addHandler(new RootRedirectHandler())
                .addHandler(new IoHandler("view"));
    }

    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.i18n.demo.service");
    }

    @Override
    public void config(Plugins plugins) {
        // 唯一库插件：解析 i18n.defaultLocale / i18n.sources，按 db,properties 顺序装载来源与门面实例
        plugins.add(new I18nPlugin(PropKit.getProp()));
    }

    @Override
    public void onStart() {
        System.out.println("[aifei-i18n-demo] 启动完成（真实 Enjoy 渲染，端口 18170）");
        System.out.println("[aifei-i18n-demo] 验证地址: http://localhost:18170/i18n/demo/page");
        System.out.println("[aifei-i18n-demo] 端点: /page /bundle /rtl /money /header /editable /save");
    }

    @Override
    public void onStop() {
        System.out.println("[aifei-i18n-demo] 已停止");
    }
}
