/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.PropKit;
import cn.hg.aifei.email.aifei.EmailPlugin;
import cn.hg.aifei.email.demo.arch.In;
import cn.hg.aifei.email.demo.arch.IoDispatcher;
import cn.hg.aifei.email.demo.arch.IoHandler;
import cn.hg.aifei.email.demo.arch.Out;
import cn.hg.aifei.email.demo.console.ConsoleLogFactory;
import cn.hg.aifei.email.demo.mock.MockMailInstaller;

/**
 * demo 装配中心：端口 18190 经 -Dundertow.port 注入；发送全链路真实走门面，底层内核客户端为 mock。
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Settings<In, Out> settings) {
        PropKit.use("app-config.txt");
        settings.setLogFactory(new ConsoleLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new IoHandler("view"));
    }

    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.email.demo.service");
    }

    @Override
    public void config(Plugins plugins) {
        // 先装 mock，再装配：装配期按通道构建客户端时即命中 mock，绝不建立 SMTP 连接
        MockMailInstaller.install();
        plugins.add(new EmailPlugin(PropKit.getProp()));
    }

    @Override
    public void onStart() {
        System.out.println("[aifei-email-demo] 启动完成，mock 发送零外网");
        System.out.println("[aifei-email-demo] 页面: http://localhost:18190/email/demo/page");
    }
}
