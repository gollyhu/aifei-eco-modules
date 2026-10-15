/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.demo.common;

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
import cn.hg.aifei.websocket.aifei.WebSocketClientPlugin;
import cn.hg.aifei.websocket.aifei.WebSocketServerPlugin;
import cn.hg.aifei.websocket.demo.ws.DemoClientListener;
import cn.hg.aifei.websocket.demo.ws.DemoServerListener;

/**
 * 装配中心：自写 HIO + aifei-websocket 的两个装配入口（服务端 + 客户端，本地回环）。
 *
 * <p>服务端插件与客户端插件**分别装配、各自独立**：服务端监听 {@code websocket.server.port}，客户端连接
 * {@code websocket.client.uri} 指向的同一端口路径，构成进程内回环，便于现场演示建连 / 收发 / 断线重连。
 * 两个监听器都是对象引用、配置里表达不了，因此经插件构造函数传入；本 demo 不使用对象编解码，故用 2 参构造器。</p>
 *
 * <p>配置错误的表现为应用启动失败（异常自 {@code Plugin.start()} 抛出），不会带着半套配置跑起来。</p>
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    /**
     * 切换配置文件的系统属性名；默认使用 {@link #DEFAULT_CONFIG}。
     */
    private static final String CONFIG_KEY = "demo.config";

    /**
     * 默认配置文件，位于 classpath 根。
     */
    private static final String DEFAULT_CONFIG = "app-config.txt";

    /**
     * 配置源；在 {@link #config(Settings)} 内载入，供 {@link #config(Plugins)} 使用。
     */
    Prop p;

    /**
     * 偏好配置：载入 {@code app-config.txt}、配置日志工厂、配置 Server / Dispatcher 与 Handler。
     *
     * @param settings aifei 偏好设置，用于挂载日志工厂、Server 与 Handler 链
     */
    @Override
    public void config(Settings<In, Out> settings) {
        // 用 -Ddemo.config=xxx.txt 可切换配置文件；端口默认由 app-config.txt 决定
        p = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        settings.setLogFactory(new Log4jLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new IoHandler(p.getBoolean("printAction", false), "view"));
    }

    /**
     * 路由配置：扫描本 demo 包下声明了 {@code @Path} 的 Service。
     *
     * @param routes 路由配置入口
     */
    @Override
    public void config(Routes routes) {
        // 演示工程不需要横切拦截器
        routes.scan("cn.hg.aifei.websocket.demo");
    }

    /**
     * 插件配置：先装配服务端（绑定端口），再装配客户端（出站连接它），保证启动即建连成功。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        plugins.add(new WebSocketServerPlugin(p, new DemoServerListener()));
        plugins.add(new WebSocketClientPlugin(p, new DemoClientListener()));
    }

    /**
     * 启动完成后打印演示关键信息（服务端端口与客户端地址），便于现场确认配置生效。
     */
    @Override
    public void onStart() {
        System.out.println("[aifei-websocket-demo] 服务端端口: " + p.get("websocket.server.port")
                + "，客户端地址: " + p.get("websocket.client.uri"));
    }

    /**
     * 停止时打印提示；WebSocket 两个实例的关闭由各自插件负责。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-websocket-demo] 已停止");
    }
}
