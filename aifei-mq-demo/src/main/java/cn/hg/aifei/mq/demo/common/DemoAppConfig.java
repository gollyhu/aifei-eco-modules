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
import cn.hg.aifei.mq.ConsumerUtil;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.aifei.MqPlugin;
import cn.hg.aifei.mq.demo.mq.DemoMessageListener;
import cn.hg.aifei.mq.demo.mq.DemoSubscriptions;
import cn.hg.aifei.mq.demo.mq.E2EventListener;
import cn.hg.aifei.mq.demo.mq.E2Topics;
import cn.hg.aifei.mq.demo.mq.MqInstanceCatalog;
import java.util.List;

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
        // 实例清单在装配早期注入，Service 首次调用即可用（本步只保存引用，解析发生在取用时）
        MqInstanceCatalog.init(p.getProperties());
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
     * 装配期订阅演示主题并把这次订阅登记进 {@link DemoSubscriptions}，随后打印关键信息。
     *
     * <p>订阅点必须是这里：{@code config(Plugins)} 只登记插件，{@code MqPlugin.start()} 在之后才建总线并
     * 注册实例，此刻 {@link ConsumerUtil#use()} 才可用。订阅只在本方法内注册一次，避免重复登记导致同一消息
     * 被多次记录；订阅失败不吞异常，启动期明确失败优于「静默无订阅」。</p>
     *
     * <p>登记时<b>不保存</b>监听器：装配期订阅由本类持有，页面因此无法取回它、也就无法取消它（接口对这类
     * 主题一律返回 {@code 10001}）。装配期订阅分两类：<b>自由 demo 主题仅主实例</b>（保住 {@code demoA} /
     * {@code demoB} 消息流从 0 起算）；<b>E2 域 topic 覆盖每个已就绪实例</b>（否则命名实例无法演示 E2 分发，
     * 见 {@code docs/aifei-mq-demo/design-e2.md} §8.1）。二者均登记为 {@code startup} 槽，页面一律不可取消。</p>
     */
    @Override
    public void onStart() {
        // 自由 demo 主题：刻意只订阅主实例，命名实例的订阅留给页面端点
        ConsumerUtil.use().subscribe(DEMO_TOPIC, listener);
        DemoSubscriptions.registerStartup(MqInstanceCatalog.MAIN, DEMO_TOPIC, DemoMessageListener.NAME);
        // E2 域 topic：每个已就绪实例都要订阅，否则命名实例上 delivered 恒为 0、命中表恒 0
        int e2Subscriptions = 0;
        for (MqInstanceCatalog.Instance it : MqInstanceCatalog.list()) {
            String instanceName = it.instanceName();
            if (!it.ready()) {
                System.out.println("[aifei-mq-demo] 跳过实例 " + instanceName + " 的 E2 域订阅：该实例尚未装配完成");
                continue;
            }
            for (String topic : E2Topics.all()) {
                // 必须用该实例自己的消费者：无参 ConsumerUtil.use() 恒指主实例
                it.consumer().subscribe(topic, new E2EventListener(instanceName));
                DemoSubscriptions.registerStartup(instanceName, topic, E2EventListener.NAME);
                e2Subscriptions++;
            }
        }
        List<String> names = MqInstanceCatalog.names();
        System.out.println("[aifei-mq-demo] mq.type: " + p.get(MQ_TYPE_KEY, "memory")
                + "，实例(" + names.size() + "): " + names
                + "，装配期已订阅: " + MqInstanceCatalog.MAIN + " / " + DEMO_TOPIC
                + " + E2 域 " + E2Topics.all() + " × " + e2Subscriptions + " 条(按实例)"
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
