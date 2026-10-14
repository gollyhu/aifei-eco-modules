/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.javawebsocket;

import cn.aifei.websocket.ClientConfig;
import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.ServerConfig;
import cn.aifei.websocket.ServerContractTest;
import cn.aifei.websocket.ServerListener;
import cn.aifei.websocket.WebSocketClient;
import cn.aifei.websocket.WebSocketKit;
import cn.aifei.websocket.WebSocketServer;

/**
 * Java-WebSocket 后端的服务端契约测试：继承 {@link ServerContractTest} 并提供本后端的实例工厂。
 *
 * <p>经 {@link WebSocketKit} 构建，与生产装配走同一条 provider 路径；断言全部来自契约基类，本类只需保证
 * 实例由 javawebsocket 后端产出。</p>
 */
public class JavaWebSocketServerTest extends ServerContractTest {

    @Override
    protected WebSocketServer newServer(ServerConfig config, ServerListener listener) {
        return WebSocketKit.createServer(config, listener);
    }

    @Override
    protected WebSocketClient newClient(ClientConfig config, ClientListener listener) {
        return WebSocketKit.createClient(config, listener);
    }

    @Override
    protected String backendType() {
        return "javawebsocket";
    }
}
