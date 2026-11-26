/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.PropKit;
import cn.hg.aifei.sms.aifei.SmsHandler;
import cn.hg.aifei.sms.aifei.SmsPlugin;
import cn.hg.aifei.sms.demo.arch.In;
import cn.hg.aifei.sms.demo.arch.IoDispatcher;
import cn.hg.aifei.sms.demo.arch.IoHandler;
import cn.hg.aifei.sms.demo.arch.Out;
import cn.hg.aifei.sms.demo.console.ConsoleLogFactory;
import cn.hg.aifei.sms.demo.mock.MockBlendInstaller;
import cn.hg.aifei.sms.demo.mock.ReceiptRecorder;
import cn.hg.aifei.sms.demo.mock.SmsIoImpl;
import cn.hg.aifei.sms.spi.SmsReceiptListener;
import java.util.ArrayList;
import java.util.List;

/** demo 装配中心：端口 18180 经 -Dundertow.port 注入；发送全程 mock，回执走真实 SmsHandler。 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    @Override
    public void config(Settings<In, Out> settings) {
        PropKit.use("app-config.txt");
        settings.setLogFactory(new ConsoleLogFactory());
        List<SmsReceiptListener> listeners = new ArrayList<SmsReceiptListener>();
        listeners.add(new ReceiptRecorder());
        // 回执 Handler 必须注册在渲染首页的 IoHandler 之前（对齐 WeixinHandler）
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new SmsHandler<In, Out>(new SmsIoImpl(), listeners))
                .addHandler(new IoHandler("view"));
    }

    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.sms.demo.service");
    }

    @Override
    public void config(Plugins plugins) {
        MockBlendInstaller.install();
        plugins.add(new SmsPlugin(PropKit.getProp()));
    }

    @Override
    public void onStart() {
        System.out.println("[aifei-sms-demo] 启动完成，mock 发送零外网");
        System.out.println("[aifei-sms-demo] 页面: http://localhost:18180/sms/demo/page");
    }

    @Override
    public void onStop() {
        System.out.println("[aifei-sms-demo] 已停止");
    }
}
