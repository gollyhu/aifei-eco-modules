/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

import cn.aifei.websocket.internal.WebSocketProviderRegistry;
import cn.aifei.websocket.spi.WebSocketProvider;

/**
 * 程序化工厂：由 {@link ClientConfig} / {@link ServerConfig} 创建客户端与服务端实例。
 *
 * <p>供非 aifei 环境或需要程序化、多连接的场景使用；在 aifei 应用内通常经装配入口以 {@code @Inject}
 * 取用。两个方法都按 {@code config.type()} 从后端注册表取 provider 并构建实例，与装配入口走同一条路径。</p>
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
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}，或后端类型未注册
     */
    public static WebSocketClient createClient(ClientConfig config, ClientListener listener) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        WebSocketProvider provider = new WebSocketProviderRegistry().getProvider(config.type());
        return provider.buildClient(config, listener);
    }

    /**
     * 创建一个服务端实例；不会自动启动。
     *
     * @param config 服务端配置
     * @param listener 服务端回调
     * @return 新建的服务端实例，初始为未启动
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}，或后端类型未注册
     */
    public static WebSocketServer createServer(ServerConfig config, ServerListener listener) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        WebSocketProvider provider = new WebSocketProviderRegistry().getProvider(config.type());
        return provider.buildServer(config, listener);
    }
}
