/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.spi;

import cn.aifei.websocket.ClientConfig;
import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.ServerConfig;
import cn.aifei.websocket.ServerListener;
import cn.aifei.websocket.WebSocketClient;
import cn.aifei.websocket.WebSocketServer;

/**
 * 传输后端扩展点：一个后端（如 Java-WebSocket）同时提供客户端与服务端两种能力。
 *
 * <p>签名中只有本模块类型，无第三方类型、无框架类型。新增后端只需实现本接口（或在
 * {@link AbstractWebSocketProvider} 上扩展）并在后端注册表里注册一行，装配逻辑无需改动。</p>
 */
public interface WebSocketProvider {

    /**
     * 返回后端类型标识。
     *
     * @return 后端类型，如 {@code "javawebsocket"}
     */
    String type();

    /**
     * 构建客户端实例；不会自动建连。
     *
     * @param config 客户端配置
     * @param listener 客户端回调
     * @return 客户端实例，初始状态为 {@code CLOSED}
     * @throws IllegalArgumentException 配置为 {@code null}、类型与本后端不一致
     */
    WebSocketClient buildClient(ClientConfig config, ClientListener listener);

    /**
     * 构建服务端实例；不会自动启动。
     *
     * @param config 服务端配置
     * @param listener 服务端回调
     * @return 服务端实例，初始为未启动
     * @throws IllegalArgumentException 配置为 {@code null}、类型与本后端不一致
     */
    WebSocketServer buildServer(ServerConfig config, ServerListener listener);
}
