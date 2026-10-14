/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.javawebsocket;

import cn.aifei.websocket.ClientConfig;
import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.ServerConfig;
import cn.aifei.websocket.ServerListener;
import cn.aifei.websocket.WebSocketClient;
import cn.aifei.websocket.WebSocketServer;
import cn.aifei.websocket.spi.AbstractWebSocketProvider;

/**
 * Java-WebSocket 后端的装配 provider：按配置构造本后端的客户端与服务端实现。
 *
 * <p>本 provider 无状态：不保存配置或实例，配置在 {@code buildClient} / {@code buildServer} 内现读现用，
 * 因此单个实例可为任意多个连接复用。装配校验（配置非空、类型与本后端一致）由基类承担。</p>
 */
public final class JavaWebSocketProvider extends AbstractWebSocketProvider {

    /**
     * 本后端在注册表中的类型标识。
     */
    private static final String TYPE = "javawebsocket";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public WebSocketClient buildClient(ClientConfig config, ClientListener listener) {
        requireConfig(config);
        requireTypeMatches(config);
        return new JavaWebSocketClient(config, listener);
    }

    @Override
    public WebSocketServer buildServer(ServerConfig config, ServerListener listener) {
        requireConfig(config);
        requireTypeMatches(config);
        return new JavaWebSocketServer(config, listener);
    }
}
