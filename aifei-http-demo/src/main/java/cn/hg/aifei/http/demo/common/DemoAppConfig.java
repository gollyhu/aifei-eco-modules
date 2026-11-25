/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.common;

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
import cn.aifei.util.StrUtil;
import cn.hg.aifei.http.aifei.HttpPlugin;
import cn.hg.aifei.http.demo.demo.DemoInterceptor;
import cn.hg.aifei.http.demo.echo.EchoHandler;

/**
 * 装配中心：自写 HIO + aifei-http 的装配入口（单个 {@link HttpPlugin}）。
 *
 * <p>出站客户端的接入方式即「在 {@code config(Plugins)} 里
 * {@code plugins.add(new HttpPlugin(prop).addInterceptor(...))}」——构造函数只持 {@code Prop}，
 * 解析校验、建客户端、把 {@code HttpClient} 注册为 {@code @Inject} 单例都发生在内核统一启动插件时的
 * {@link HttpPlugin#start()}，配置有误即应用启动失败，不留半装配状态。</p>
 *
 * <p>拦截器必须<b>在 {@code start()} 之前</b>经 {@code addInterceptor} 按序登记：模块不从容器按类型取拦截器，
 * 顺序就是这里的调用顺序。本 demo 用同一个记录型拦截器登记 3 个实例（{@code demo-a/b/c}），
 * 以便页面直接看到「进入正序、收尾逆序」。</p>
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
     * demo 回环目标基址配置键（只允许指向本机自身）。
     */
    public static final String SELF_BASE_URL_KEY = "demo.self-base-url";

    /**
     * 回环目标基址默认值，与 {@code start-demo.bat} 的端口保持一致。
     */
    public static final String DEFAULT_SELF_BASE_URL = "http://127.0.0.1:18120";

    /**
     * 可控回环目标端点路径，回环基址 + 本路径即演示端点的请求目标。
     */
    public static final String ECHO_PATH = "/demo/echo";

    /**
     * 演示用拦截器名称（进入顺序 = 登记顺序）。
     */
    public static final String[] INTERCEPTOR_NAMES = {"demo-a", "demo-b", "demo-c"};

    /**
     * 回环基址（已去掉结尾斜杠）；装配期由 {@link #config(Settings)} 写入，供 Service 与 echo 使用。
     */
    private static volatile String selfBaseUrl = DEFAULT_SELF_BASE_URL;

    /**
     * 配置源；在 {@link #config(Settings)} 内载入，供 {@link #config(Plugins)} 与 {@link #onStart()} 使用。
     */
    Prop p;

    /**
     * 偏好配置：载入 {@code app-config.txt}，装配日志工厂、Server 与两级 Handler。
     *
     * <p>Handler 顺序即责任链顺序：{@link RootRedirectHandler} 在最前，命中 {@code /} 时以 302 跳转到演示主页；
     * {@link EchoHandler} 次之，命中 {@code /demo/echo} 时自行应答（指定状态码 / 延迟 / gzip / 302）；
     * 其余路径交棒给 {@link IoHandler}（路由到 Service 或静态资源）。</p>
     *
     * @param settings aifei 偏好设置，用于挂载日志工厂、Server 与 Handler 链
     */
    @Override
    public void config(Settings<In, Out> settings) {
        // 用 -Ddemo.config=xxx.txt 可切换配置文件；端口由 -Dundertow.port 决定
        p = PropKit.use(System.getProperty(CONFIG_KEY, DEFAULT_CONFIG));
        String baseUrl = p.get(SELF_BASE_URL_KEY, DEFAULT_SELF_BASE_URL);
        selfBaseUrl = trimTrailingSlash(baseUrl);
        settings.setLogFactory(new Log4jLogFactory());
        settings.setServer(new UndertowServer(), new IoDispatcher())
                .addHandler(new RootRedirectHandler())
                .addHandler(new EchoHandler())
                .addHandler(new IoHandler(p.getBoolean("printAction", false), "view"));
    }

    /**
     * 路由配置：只扫描 Service 包下声明了 {@code @Path} 的类（HIO / 记录型类不参与路由）。
     *
     * @param routes 路由配置入口
     */
    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.http.demo.service");
    }

    /**
     * 插件配置：装配 aifei-http（由此获得 {@code @Inject HttpClient} 单例），并在启动前登记拦截器。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        HttpPlugin httpPlugin = new HttpPlugin(p);
        for (int i = 0; i < INTERCEPTOR_NAMES.length; i++) {
            // 只有第一个实例参与「请求数 / 尝试数 / 重试数」计数，避免 3 个实例把计数放大 3 倍
            httpPlugin.addInterceptor(new DemoInterceptor(INTERCEPTOR_NAMES[i], i == 0));
        }
        plugins.add(httpPlugin);
    }

    /**
     * 启动完成后打印关键信息，便于确认装配结果与验证地址。
     */
    @Override
    public void onStart() {
        System.out.println("[aifei-http-demo] http.gzip: " + p.get("http.gzip", "false")
                + "，http.retry-times: " + p.get("http.retry-times", "0")
                + "，回环基址: " + selfBaseUrl
                + "，拦截器: " + String.join("/", INTERCEPTOR_NAMES)
                + "，验证地址: http://localhost:18120/http/demo/state");
    }

    /**
     * 停止时打印提示；客户端实例的清理由 {@link HttpPlugin#stop()} 负责。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-http-demo] 已停止");
    }

    /**
     * 当前回环基址（形如 {@code http://127.0.0.1:18120}，无结尾斜杠）。
     *
     * @return 回环基址
     */
    public static String selfBaseUrl() {
        return selfBaseUrl;
    }

    /**
     * 可控回环目标端点的完整 URL。
     *
     * @return {@code 回环基址 + /demo/echo}
     */
    public static String echoUrl() {
        return selfBaseUrl + ECHO_PATH;
    }

    /**
     * 把页面传入的相对路径拼成回环绝对 URL，并拒绝任何外部地址（禁外网硬约束）。
     *
     * @param path 以 {@code /} 开头的相对路径，空白表示 {@link #ECHO_PATH}
     * @return 回环绝对 URL
     * @throws IllegalArgumentException 路径含协议前缀（{@code ://}）时抛出
     */
    public static String resolveSelfUrl(String path) {
        String p = StrUtil.isBlank(path) ? ECHO_PATH : path.trim();
        if (p.contains("://")) {
            throw new IllegalArgumentException("本 demo 只允许访问本机回环路径，禁止外部地址: " + p);
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return selfBaseUrl + p;
    }

    private static String trimTrailingSlash(String url) {
        String u = StrUtil.isBlank(url) ? DEFAULT_SELF_BASE_URL : url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
