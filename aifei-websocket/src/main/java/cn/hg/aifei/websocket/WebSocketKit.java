/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import cn.hg.aifei.websocket.internal.jdk.JdkWebSocketClient;
import cn.hg.aifei.websocket.internal.jdk.JdkWebSocketServer;

/**
 * 程序化工厂：由 {@link ClientConfig} / {@link ServerConfig} 创建客户端与服务端实例。
 *
 * <p>供非 aifei 环境或需要程序化、多连接的场景使用；在 aifei 应用内通常经装配入口以 {@code @Inject}
 * 取用。两个方法都<b>直接构建内建 JDK 实现</b>，不经任何注册表或 provider，与两个装配入口走同一条路径。</p>
 *
 * <p>返回的实例不会自动建连或启动，由调用方决定何时 {@link WebSocketClient#connect()} /
 * {@link WebSocketServer#start()}。</p>
 */
public final class WebSocketKit {

    /**
     * 禁止创建实例。
     */
    private WebSocketKit() {
    }

    /**
     * 创建一个客户端实例；不会自动建连。
     *
     * @param config 客户端配置
     * @param listener 客户端回调
     * @return 新建的客户端实例，初始状态为 {@link ConnectionState#CLOSED}
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}，或跨字段校验不通过
     */
    public static WebSocketClient createClient(ClientConfig config, ClientListener listener) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        return new JdkWebSocketClient(config, listener);
    }

    /**
     * 创建一个服务端实例；不会自动启动。
     *
     * @param config 服务端配置
     * @param listener 服务端回调
     * @return 新建的服务端实例，初始为未启动
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}
     */
    public static WebSocketServer createServer(ServerConfig config, ServerListener listener) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        return new JdkWebSocketServer(config, listener);
    }
}