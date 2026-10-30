/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.demo.common;

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
import cn.hg.aifei.oss.aifei.OssPlugin;

/**
 * 装配中心：自写 HIO + aifei-oss 装配入口。
 *
 * <p>{@code OssPlugin} 在 {@code start()} 内按 {@code oss.*} 配置构建各平台，
 * 并把默认平台注册为 {@code @Inject OssClient} 单例；配置有误即应用启动失败。</p>
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
        routes.scan("cn.hg.aifei.oss.demo");
    }

    /**
     * 插件配置：装配 aifei-oss。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        plugins.add(new OssPlugin(p));
    }

    /**
     * 启动完成后打印演示关键信息，便于现场确认配置生效。
     */
    @Override
    public void onStart() {
        System.out.println("[aifei-oss-demo] 默认平台: " + p.get("oss.default-platform")
                + "，访问 http://localhost:" + System.getProperty("undertow.port", "18092") + "/oss/demo/state");
    }

    /**
     * 停止时打印提示；客户端登记表由 {@code OssPlugin#stop()} 清理。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-oss-demo] 已停止");
    }
}
