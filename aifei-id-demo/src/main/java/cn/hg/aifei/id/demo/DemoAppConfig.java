/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.Prop;
import cn.aifei.util.PropKit;
import cn.hg.aifei.id.aifei.IdPlugin;
import cn.hg.aifei.id.demo.arch.In;
import cn.hg.aifei.id.demo.arch.IoDispatcher;
import cn.hg.aifei.id.demo.arch.IoHandler;
import cn.hg.aifei.id.demo.arch.Out;
import cn.hg.aifei.id.demo.console.ConsoleLogFactory;

/**
 * 装配中心：aifei-id 的演示装配入口。
 *
 * <p>接入方式只有一句：{@code config(Plugins)} 里 {@code plugins.add(new IdPlugin(prop))}。
 * {@code IdPlugin} 构造只保存 {@link Prop} 引用，配置解析、workerId 获取、生成器装配与
 * {@code AopKit} 单例注册全部发生在内核统一启动插件时的 {@code start()}；配置有误即应用启动失败，
 * 不留半装配状态。</p>
 *
 * <p>策略是<b>进程级</b>、不可运行期切换，因此本 demo 提供两份配置：{@code app-config.txt}（雪花）与
 * {@code app-config-segment.txt}（号段 + {@code local} 存储），用 {@code -Ddemo.config=...} 选择，
 * 对应 {@code start-demo.bat} / {@code start-demo-segment.bat} 两个脚本。</p>
 *
 * <p>本类另持一份 {@code id.*} 的<b>只读镜像</b>（{@link #initMirror(Prop)}）：页面要展示的
 * strategy / workerSource / workerId / segmentStore / step 均取自 <b>demo 自身的配置</b>，
 * <b>不是</b>模块提供的状态 API（aifei-id 无状态查询 API）。</p>
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    /**
     * 切换配置文件的系统属性名；默认使用 {@link #DEFAULT_CONFIG}。
     */
    public static final String CONFIG_KEY = "demo.config";

    /**
     * 默认配置文件（雪花策略），位于 classpath 根。
     */
    public static final String DEFAULT_CONFIG = "app-config.txt";

    /**
     * 号段策略配置文件（{@code id.segment.store=local}，离线可跑）。
     */
    public static final String SEGMENT_CONFIG = "app-config-segment.txt";

    // ===== demo 自身配置的只读镜像（仅用于页面展示）=====================================

    private static volatile String strategy = "snowflake";
    private static volatile String workerSource = "config";
    private static volatile Integer workerId = null;
    private static volatile String segmentStore = "cache";
    private static volatile int segmentStep = 1000;

    /**
     * 配置源；在 {@link #config(Settings)} 内载入，供 {@link #config(Plugins)} 使用。
     */
    Prop p;

    /**
     * 偏好配置：载入配置文件、装配日志工厂、Server / Dispatcher / Handler。
     *
     * @param settings aifei 偏好设置，用于挂载日志工厂、Server、Handler 链
     */
    @Override
    public void config(Settings<In, Out> settings) {
        // -Ddemo.config=app-config-segment.txt 切到号段策略；端口由 -Dundertow.port 决定（18140）
        p = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        // 展示用镜像：解析与校验仍由 IdPlugin.start() 负责，此处只读不改
        initMirror(p);

        // aifei 内核默认 NoLogFactory 每次取日志即抛，必须给一个真实实现；此处零日志绑定
        settings.setLogFactory(new ConsoleLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new IoHandler("view"));
    }

    /**
     * 路由配置：只扫描本 demo 的 Service 包。
     *
     * @param routes 路由配置入口
     */
    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.id.demo.service");
    }

    /**
     * 插件配置：装配 aifei-id（由此获得 {@code IdKit} 与可 {@code @Inject} 的 {@code IdGenerator}）。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        // 顺序纪律：本 demo 用 id.worker.source=config 与 id.segment.store=local，无 cache / lock 依赖，
        // 故无需在 CachePlugin / LockPlugin 之后登记；改用 cache / lock 时必须排在其后。
        plugins.add(new IdPlugin(p));
    }

    /**
     * 启动完成后打印生效配置与验证地址；{@code IdKit} 此刻已可用。
     */
    @Override
    public void onStart() {
        System.out.println("[aifei-id-demo] 配置文件: " + System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        System.out.println("[aifei-id-demo] strategy=" + strategy
                + "，worker.source=" + workerSource
                + "，workerId=" + (workerId == null ? "—" : workerId)
                + "，segment.store=" + segmentStore
                + "，segment.step=" + segmentStep);
        System.out.println("[aifei-id-demo] 验证地址: http://localhost:18140/id/demo/page"
                + "（JSON: /id/demo/state /id/demo/next /id/demo/batch?size=2000 /id/demo/segment?size=450）");
    }

    /**
     * 停止时打印提示；生成器与 workerId 来源的收敛由 {@code IdPlugin#stop()} 负责。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-id-demo] 已停止");
    }

    // ===== 只读镜像（供 Service 组装 state / segment 端点）================================

    /**
     * 生效策略（demo 自身配置的镜像）。
     *
     * @return {@code snowflake} 或 {@code segment}
     */
    public static String strategy() {
        return strategy;
    }

    /**
     * workerId 来源（demo 自身配置的镜像）。
     *
     * @return {@code config} / {@code cache} / {@code lock}
     */
    public static String workerSource() {
        return workerSource;
    }

    /**
     * 静态配置的 workerId（仅 {@code id.worker.source=config} 时有值）。
     *
     * @return workerId，未配置时为 null
     */
    public static Integer workerId() {
        return workerId;
    }

    /**
     * 号段存储类型（demo 自身配置的镜像）。
     *
     * @return {@code cache} 或 {@code local}
     */
    public static String segmentStore() {
        return segmentStore;
    }

    /**
     * 号段长度（demo 自身配置的镜像）；页面按它自算段边界 {@code index = (id - 1) / step}。
     *
     * @return 号段长度，恒 ≥ 1
     */
    public static int segmentStep() {
        return segmentStep;
    }

    /**
     * 把 {@code id.*} 的展示值镜像到静态字段；缺省值与模块默认值一致。
     *
     * @param prop 已载入的配置源
     */
    private static void initMirror(Prop prop) {
        strategy = trim(prop.get("id.strategy", "snowflake"), "snowflake");
        workerSource = trim(prop.get("id.worker.source", "config"), "config");
        workerId = prop.getInt("id.workerId", null);
        segmentStore = trim(prop.get("id.segment.store", "cache"), "cache");

        Integer step = prop.getInt("id.segment.step", Integer.valueOf(1000));
        segmentStep = step == null || step.intValue() < 1 ? 1000 : step.intValue();
    }

    /**
     * 读取配置字符串并去空白；空值回退默认值。
     *
     * @param value        配置原值
     * @param defaultValue 默认值
     * @return 去空白后的值
     */
    private static String trim(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }
}
