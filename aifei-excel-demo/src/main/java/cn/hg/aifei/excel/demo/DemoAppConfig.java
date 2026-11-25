/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.excel.demo;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.PropKit;
import cn.hg.aifei.excel.demo.arch.In;
import cn.hg.aifei.excel.demo.arch.IoDispatcher;
import cn.hg.aifei.excel.demo.arch.IoHandler;
import cn.hg.aifei.excel.demo.arch.Out;
import cn.hg.aifei.excel.demo.arch.RootRedirectHandler;
import cn.hg.aifei.excel.demo.console.ConsoleLogFactory;

/**
 * 装配中心：aifei-excel 的演示装配入口。
 *
 * <p>本 demo 演示库模块（SDK）能力，库模块自身无 Plugin / 无配置键，因此本类
 * {@link #config(Plugins)} 为空实现——不装配任何插件；{@code Excel} / {@code ExcelKit}
 * 随取随用，无需装配期。HTTP 端口由 {@code -Dundertow.port=18150} 传入。</p>
 *
 * <p>日志工厂用 demo 自带的 {@link ConsoleLogFactory}（System.out），零日志绑定；
 * Handler 链首 {@link RootRedirectHandler} 把根路径重定向到主页面，次 {@link IoHandler}，
 * 静态资源（webapp/assets）由内核基于 webroot 默认提供。</p>
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Settings<In, Out> settings) {
        // 先载入配置：IoHandler 构造内经 PropKit.getBoolean("enjoyDevMode") 读取模板热加载开关
        PropKit.use("app-config.txt");
        // aifei 内核默认 NoLogFactory 每次取日志即抛，必须给一个真实实现；此处零日志绑定
        settings.setLogFactory(new ConsoleLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new RootRedirectHandler())
                .addHandler(new IoHandler("view"));
    }

    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.excel.demo.service");
    }

    @Override
    public void config(Plugins plugins) {
        // 库模块无 Plugin、无装配期：Excel / ExcelKit 随取随用，故无插件可注册。
    }

    @Override
    public void onStart() {
        System.out.println("[aifei-excel-demo] 启动完成（库模块 SDK 演示，无 Plugin 装配）");
        System.out.println("[aifei-excel-demo] 验证地址: http://localhost:18150/excel/demo/page");
        System.out.println("[aifei-excel-demo] 端点: /excel/demo/page /state /parse(POST multipart)"
                + " /export-once /export-stream?rows=50000");
    }

    @Override
    public void onStop() {
        System.out.println("[aifei-excel-demo] 已停止");
    }
}
