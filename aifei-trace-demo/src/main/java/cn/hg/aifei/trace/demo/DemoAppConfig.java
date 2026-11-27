/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.demo;

import cn.aifei.config.AifeiConfig;
import cn.aifei.config.Plugins;
import cn.aifei.config.Routes;
import cn.aifei.config.Settings;
import cn.hg.aifei.trace.TraceLogFactory;
import cn.hg.aifei.trace.demo.arch.In;
import cn.hg.aifei.trace.demo.arch.IoDispatcher;
import cn.hg.aifei.trace.demo.arch.IoHandler;
import cn.hg.aifei.trace.demo.arch.Out;
import cn.hg.aifei.trace.demo.console.ConsoleLogFactory;
import cn.hg.aifei.trace.web.TraceHandler;
import cn.hg.aifei.trace.web.TraceIdExtractor;
import cn.aifei.server.undertow.UndertowServer;
import cn.aifei.util.PropKit;

/**
 * 装配中心：aifei-trace 的演示装配入口。
 *
 * <p>本类是 {@code aifei-trace} 两大「使用方装配动作」的唯一直观落点：</p>
 *
 * <p>① 日志贯通（装配期一次性）：{@code settings.setLogFactory(new TraceLogFactory(new ConsoleLogFactory()))} ——
 * 用 demo 自带的极简 {@link ConsoleLogFactory}（{@code System.out} 输出，不引任何 slf4j / log4j 绑定）
 * 作为被装饰的底层工厂，全应用的 Log 产出自动携带 {@code [traceId] } 前缀。</p>
 *
 * <p>② Web 入口：{@code TraceHandler} 必须<b>第一个 addHandler</b> 进 Handler 链（链顺序即语义，
 * 只有第一位才能覆盖全部下游 Handler），并配 extractor 从请求头 {@link #TRACE_HEADER} 提取上游透传的 traceId。
 * {@code cn.aifei.core.Input} 公开接口只有参数读取能力、无 header 访问，故 demo 的 {@link In}
 * 实现暴露 {@code getHeader(...)}，extractor 写作 {@code in -> in.getHeader("X-Trace-Id")}。</p>
 *
 */
public class DemoAppConfig implements AifeiConfig<In, Out> {

    /**
     * 默认配置文件，位于 classpath 根。
     */
    public static final String DEFAULT_CONFIG = "app-config.txt";

    /**
     * 提取上游 traceId 的请求头名；页面「提取演示」场景即携带该头发请求。
     */
    public static final String TRACE_HEADER = "X-Trace-Id";

    /**
     * 偏好配置：装配日志工厂（TraceLogFactory 装饰 ConsoleLogFactory）、Server 与两级 Handler。
     *
     * <p>Handler 顺序即责任链顺序：{@link TraceHandler} 在<b>第一位</b>（绑定 → 透传下游 → finally 清理），
     * 最后是 {@link IoHandler}（路由到 Service 或 404）。</p>
     *
     * @param settings aifei 偏好设置，用于挂载日志工厂、Server 与 Handler 链
     */
    @Override
    public void config(Settings<In, Out> settings) {
        // 载入配置文件（classpath 根的 app-config.txt；-Ddemo.config 可切换）
        PropKit.use(System.getProperty("demo.config", DEFAULT_CONFIG));

        // ② TraceLogFactory 装配：装饰 ConsoleLogFactory，全应用日志自动带 [traceId] 前缀
        settings.setLogFactory(new TraceLogFactory(new ConsoleLogFactory()));

        // 端口由 -Dundertow.port 决定（start-demo.bat 传入 18130）
        settings.setServer(new UndertowServer(), new IoDispatcher())
                // ① TraceHandler 必须第一个 addHandler：extractor 演示从请求头提取，缺失/空白回退生成
                .addHandler(new TraceHandler<In, Out>(new TraceIdExtractor<In>() {
                    @Override
                    public String extract(In in) {
                        return in.getHeader(TRACE_HEADER);
                    }
                }))
                .addHandler(new IoHandler("view"));
    }

    /**
     * 路由配置：只扫描 Service 包下声明了 {@code @Path} 的类。
     *
     * @param routes 路由配置入口
     */
    @Override
    public void config(Routes routes) {
        routes.scan("cn.hg.aifei.trace.demo.service");
    }

    /**
     * 插件配置：aifei-trace 无 Plugin（纯静态门面 + 使用方显式构造接入构件），
     * 全部装配动作已在 {@link #config(Settings)} 内完成，故此处不登记任何插件。
     *
     * @param plugins 插件注册入口
     */
    @Override
    public void config(Plugins plugins) {
        // 无插件：trace 的接入点（Handler 链、LogFactory）都是框架配置对象，无需 Plugin 中转
    }

    /**
     * 启动完成后打印关键信息，便于确认装配结果与验证地址。
     */
    @Override
    public void onStart() {
        System.out.println("[aifei-trace-demo] 日志工厂链: TraceLogFactory -> ConsoleLogFactory（System.out，无日志绑定）");
        System.out.println("[aifei-trace-demo] Handler 链: TraceHandler（第一位，提取头 " + TRACE_HEADER + "）-> IoHandler");
        System.out.println("[aifei-trace-demo] 验证地址: http://localhost:18130/trace/demo/page");
    }

    /**
     * 停止时打印提示。
     */
    @Override
    public void onStop() {
        System.out.println("[aifei-trace-demo] 已停止");
    }
}
