/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.common;

import cn.aifei.aop.AopKit;
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
import cn.hg.aifei.job.JobKit;
import cn.hg.aifei.job.JobListener;
import cn.hg.aifei.job.aifei.JobPlugin;
import cn.hg.aifei.job.demo.job.DemoCronJob;
import cn.hg.aifei.job.demo.job.DemoFailingJob;
import cn.hg.aifei.job.demo.job.DemoFixedDelayJob;
import cn.hg.aifei.job.demo.job.DemoFixedRateJob;
import cn.hg.aifei.job.demo.job.DemoJobListener;
import cn.hg.aifei.job.demo.job.DemoShutdownJob;
import cn.hg.aifei.job.demo.job.DemoSlowJob;
import java.util.Set;

/**
 * 装配中心：自写 HIO + aifei-job 的装配入口（单个 {@link JobPlugin}）。
 *
 * <p>定时任务的接入方式即「在 {@code config(Plugins)} 里 {@code plugins.add(new JobPlugin(prop))}」——
 * 构造函数只持 {@code Prop}，配置解析、扫包、任务装配与线程池创建都发生在内核统一启动插件时的
 * {@link JobPlugin#start()}，配置有误即应用启动失败，不留半装配状态。</p>
 *
 * <p>任务类与监听器必须在容器里是<b>单例</b>：{@code JobPlugin.start()} 按类型从容器取一次并长期持有
 * （取不到 → 任务启动失败；多例 → 启动失败；监听器取不到只是「无事件送达方」）。本 demo 在
 * {@link #config(Settings)} 里用 {@code AopKit.addSingletonObject(...)} 显式注册，等价于
 * {@code aifei-mq-demo} 里生产者 / 消费者单例的注册方式。</p>
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
     * 配置源；在 {@link #config(Settings)} 内载入，供后续两个 config 方法与 {@link #onStart()} 使用。
     */
    Prop p;

    /**
     * 偏好配置：载入 {@code app-config.txt}、配置日志工厂、Server / Dispatcher / Handler，并注册单例 Bean。
     *
     * @param settings aifei 偏好设置，用于挂载日志工厂、Server、Handler 链
     */
    @Override
    public void config(Settings<In, Out> settings) {
        // 用 -Ddemo.config=xxx.txt 可切换配置文件；端口由 -Dundertow.port 决定
        p = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        // 展示用的三个 job.* 参数在装配早期镜像一份（解析与校验仍由 JobPlugin.start() 负责）
        DemoRuntimeConfig.init(p);
        settings.setLogFactory(new Log4jLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new RootRedirectHandler())
                .addHandler(new IoHandler(p.getBoolean("printAction", false), "view"));
        // 单例必须在插件 start() 之前进容器：JobPlugin 启动时按类型取一次
        registerSingletons();
    }

    /**
     * 路由配置：扫描本 demo 包下声明了 {@code @Path} 的 Service。
     *
     * @param routes 路由配置入口
     */
    @Override
    public void config(Routes routes) {
        // 演示工程不需要横切拦截器
        routes.scan("cn.hg.aifei.job.demo");
    }

    /**
     * 插件配置：装配 aifei-job（由此获得任务扫描、调度内核与 {@code JobKit} 观测面）。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        plugins.add(new JobPlugin(p));
    }

    /**
     * 启动完成后打印已注册任务与页面地址；{@code JobKit} 此刻已可用。
     */
    @Override
    public void onStart() {
        Set<String> names = JobKit.names();
        System.out.println("[aifei-job-demo] 已注册任务(" + names.size() + "): " + names);
        System.out.println("[aifei-job-demo] 执行层: pool.size=" + DemoRuntimeConfig.poolSize()
                + "，pool.queue=" + DemoRuntimeConfig.queueCapacity()
                + "，shutdown.timeoutMillis=" + DemoRuntimeConfig.shutdownTimeoutMillis());
        System.out.println("[aifei-job-demo] 演示页: http://localhost:18110/job/demo/page"
                + "（静态地址: /view/job/demo.html）");
    }

    /**
     * 停止时打印提示；调度内核的关闭由 {@link JobPlugin#stop()} 负责。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-job-demo] 已停止");
    }

    /**
     * 把 6 个演示任务与 1 个监听器注册为容器单例。
     *
     * <p>任务类自带 {@code @Singleton(true)}（模块据此判定单例语义），此处注册的是实例本身，故方法调用不经代理；
     * 监听器按接口类型 {@link JobListener} 注册——模块正是按该类型从容器取监听器，
     * <b>不注册则一条事件都不会送达</b>。</p>
     */
    private static void registerSingletons() {
        AopKit kit = AopKit.get();
        kit.addSingletonObject(DemoCronJob.class, new DemoCronJob());
        kit.addSingletonObject(DemoFixedRateJob.class, new DemoFixedRateJob());
        kit.addSingletonObject(DemoFixedDelayJob.class, new DemoFixedDelayJob());
        kit.addSingletonObject(DemoFailingJob.class, new DemoFailingJob());
        kit.addSingletonObject(DemoSlowJob.class, new DemoSlowJob());
        kit.addSingletonObject(DemoShutdownJob.class, new DemoShutdownJob());
        kit.addSingletonObject(JobListener.class, new DemoJobListener());
    }
}
