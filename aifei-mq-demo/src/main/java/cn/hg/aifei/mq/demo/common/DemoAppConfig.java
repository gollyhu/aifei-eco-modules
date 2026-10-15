/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.common;

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
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqKit;
import cn.hg.aifei.mq.aifei.MqPlugin;
import cn.hg.aifei.mq.demo.mq.DemoMessageListener;
import cn.hg.aifei.mq.demo.mq.DemoMessageLog;

/**
 * 装配中心：自写 HIO + aifei-mq 的装配入口（单个 {@link MqPlugin}）。
 *
 * <p>消息后端的接入方式即「在 {@code config(Plugins)} 里 {@code plugins.add(new MqPlugin(prop))}」——
 * 构造函数只持 {@code Prop}，配置解析与建总线发生在内核统一启动插件时的 {@code MqPlugin.start()}，
 * 配置有误即应用启动失败，不留半装配状态。</p>
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    /**
     * 切换配置文件的系统属性名；默认使用 {@link #DEFAULT_CONFIG}。
     */
    public static final String CONFIG_KEY = "demo.config";

    /**
     * 默认配置文件，位于 classpath 根。
     */
    public static final String DEFAULT_CONFIG = "app-config.txt";

    /**
     * 消息后端类型配置键；后端标识字面量 {@code "memory"} 的读取来源。
     */
    public static final String MQ_TYPE_KEY = "mq.type";

    /**
     * 演示主题；订阅与发送都围绕它展开。
     */
    public static final String DEMO_TOPIC = "aifei.mq.demo.topic";

    /**
     * 配置源；在 {@link #config(Settings)} 内载入，供 {@link #config(Plugins)} 与 {@link #onStart()} 使用。
     */
    Prop p;

    /**
     * 订阅用监听器；订阅发生在 {@link #onStart()}，此处持有作为单例注册给消费者。
     */
    final MessageListener listener = new DemoMessageListener();

    /**
     * 偏好配置：载入 {@code app-config.txt}、配置日志工厂、配置 Server / Dispatcher 与 Handler。
     *
     * @param settings aifei 偏好设置，用于挂载日志工厂、Server 与 Handler 链
     */
    @Override
    public void config(Settings<In, Out> settings) {
        // 用 -Ddemo.config=xxx.txt 可切换配置文件；端口由 -Dundertow.port 决定
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
        routes.scan("cn.hg.aifei.mq.demo");
    }

    /**
     * 插件配置：装配 aifei-mq（由此获得 {@code @Inject Producer} / {@code @Inject Consumer} 两个单例）。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        plugins.add(new MqPlugin(p));
    }

    /**
     * 装配期订阅演示主题，写装配快照并打印关键信息。
     *
     * <p>订阅点必须是这里：{@code config(Plugins)} 只登记插件，{@code MqPlugin.start()} 在之后才建总线并
     * 注册单例，此刻 {@link MqKit#consumer()} 才可用。本版无 {@code unsubscribe}，重复订阅会重复登记，
     * 故订阅只在本方法内注册一次；订阅失败不吞异常，启动期明确失败优于「静默无订阅」。</p>
     */
    @Override
    public void onStart() {
        MqKit.consumer().subscribe(DEMO_TOPIC, listener);
        DemoMessageLog.markStartup(p.get(MQ_TYPE_KEY, "memory"), DEMO_TOPIC, DemoMessageListener.NAME);
        System.out.println("[aifei-mq-demo] mq.type: " + p.get(MQ_TYPE_KEY, "memory")
                + "，已订阅主题: " + DEMO_TOPIC
                + "，验证地址: http://localhost:18100/mq/demo/state");
    }

    /**
     * 停止时打印提示；总线的关闭由 {@link MqPlugin#stop()} 负责。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-mq-demo] 已停止");
    }
}
