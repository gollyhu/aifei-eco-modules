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
import cn.aifei.websocket.WebSocketConfig;
import cn.aifei.websocket.WebSocketServer;

/**
 * 传输后端 provider 的装配基类：承载公共装配校验，不承载任何传输逻辑。
 *
 * <p>实现必须无状态：不得声明实例字段保存配置或实例；配置在 {@code buildClient} / {@code buildServer}
 * 内经构造参数现读现用。客户端与服务端实例由各子类在自己的构建方法内创建，基类不提供实例创建钩子。</p>
 */
public abstract class AbstractWebSocketProvider implements WebSocketProvider {

    @Override
    public abstract String type();

    @Override
    public abstract WebSocketClient buildClient(ClientConfig config, ClientListener listener);

    @Override
    public abstract WebSocketServer buildServer(ServerConfig config, ServerListener listener);

    /**
     * 校验配置非 {@code null}。
     *
     * @param config 待校验的配置
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     */
    protected final void requireConfig(WebSocketConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
    }

    /**
     * 校验配置的 {@code type} 与本 provider 的 {@link #type()} 一致。
     *
     * @param config 待校验的配置
     * @throws IllegalArgumentException 两者不一致
     */
    protected final void requireTypeMatches(WebSocketConfig config) {
        String type = type();
        if (!type.equals(config.type())) {
            throw new IllegalArgumentException(
                    "provider type mismatch: expected " + type + " but config type is " + config.type()
            );
        }
    }
}
