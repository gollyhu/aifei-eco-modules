/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.aifei;

import cn.aifei.aop.AopKit;
import cn.aifei.plugin.Plugin;
import cn.aifei.util.Prop;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketKit;
import cn.hg.aifei.websocket.WebSocketServer;
import cn.hg.aifei.websocket.spi.MessageCodec;

/**
 * 服务端装配入口：应用启动时按 {@code websocket.server.*} 配置构建服务端、绑定监听端口，并把它注册为
 * {@code @Inject WebSocketServer} 单例；应用停止时停止服务端。
 *
 * <p>在 {@code AifeiConfig#config(Plugins)} 内 {@code plugins.add(new WebSocketServerPlugin(prop, listener))}
 * 或 {@code plugins.add(new WebSocketServerPlugin(prop, listener, codec))} 完成装配。构造函数只持有
 * {@code Prop}、监听器与编解码器引用，不解析、不校验、不监听端口；配置解析与启动在 {@link #start()} 内完成，
 * 配置有误即抛出异常使应用启动失败，不留半装配状态。</p>
 *
 * <p>监听器与编解码器都是对象引用，配置里表达不了，因此一并经构造函数传入：监听器为必传，编解码器为可选
 * （传 {@code null} 即不使用对象编解码）。</p>
 *
 * <p>与客户端各自独立装配：只做服务端的应用仅装配本插件，无需配置 {@code websocket.client.*}。
 * {@code websocket.server.port} 为接入层必填键，缺失即启动失败。</p>
 */
public class WebSocketServerPlugin implements Plugin {

    /**
     * 配置源；解析发生在 {@link #start()}。
     */
    private final Prop prop;

    /**
     * 服务端回调；启动时交给实例。
     */
    private final ServerListener listener;

    /**
     * 消息编解码器；{@code null} 表示不使用对象编解码。
     */
    private final MessageCodec codec;

    /**
     * 已装配的服务端实例；{@code null} 表示尚未装配，{@link #start()} 与 {@link #stop()} 据此保持幂等。
     */
    private WebSocketServer server;

    /**
     * 创建插件（不使用对象编解码）；只保存配置与监听器引用，不做任何解析、校验或端口监听。
     *
     * @param prop 配置源
     * @param listener 服务端回调
     * @throws IllegalArgumentException {@code prop} 或 {@code listener} 为 {@code null}
     */
    public WebSocketServerPlugin(Prop prop, ServerListener listener) {
        this(prop, listener, null);
    }

    /**
     * 创建插件并注入消息编解码器；只保存配置、监听器与编解码器引用，不做任何解析、校验或端口监听。
     *
     * <p>编解码器是对象引用、配置里表达不了，故经本构造函数传入；{@code codec} 非 {@code null} 时会在
     * {@link #start()} 内设入配置对象，从而使 {@code broadcast(Object)} 可用。</p>
     *
     * @param prop 配置源
     * @param listener 服务端回调
     * @param codec 消息编解码器；可为 {@code null}，表示不使用对象编解码
     * @throws IllegalArgumentException {@code prop} 或 {@code listener} 为 {@code null}
     */
    public WebSocketServerPlugin(Prop prop, ServerListener listener, MessageCodec codec) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        this.prop = prop;
        this.listener = listener;
        this.codec = codec;
    }

    /**
     * 解析配置、构建服务端、绑定监听端口并注册 {@code @Inject} 单例；重复启动不会重复启动。
     *
     * <p>构造函数注入了编解码器时设入配置对象，之后才创建实例，确保对象编解码在主路径可用。</p>
     *
     * @throws IllegalArgumentException 配置缺失、非法或后端类型未注册
     */
    @Override
    public synchronized void start() {
        if (server != null) {
            return;
        }
        ServerConfig config = WebSocketConfigLoader.loadServerConfig(prop);
        if (codec != null) {
            config.setCodec(codec);
        }
        WebSocketServer created = WebSocketKit.createServer(config, listener);
        created.start();
        AopKit.get().addSingletonObject(WebSocketServer.class, created);
        this.server = created;
    }

    /**
     * 停止已装配的服务端；重复停止不会重复停止。
     */
    @Override
    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
    }
}
