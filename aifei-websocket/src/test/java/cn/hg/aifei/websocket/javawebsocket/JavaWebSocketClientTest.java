/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.javawebsocket;

import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientContractTest;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketKit;
import cn.hg.aifei.websocket.WebSocketServer;

/**
 * Java-WebSocket 后端的客户端契约测试：继承 {@link ClientContractTest} 并提供本后端的实例工厂。
 *
 * <p>经 {@link WebSocketKit} 构建，与生产装配走同一条 provider 路径；断言全部来自契约基类，本类只需保证
 * 实例由 javawebsocket 后端产出。</p>
 */
public class JavaWebSocketClientTest extends ClientContractTest {

    @Override
    protected WebSocketClient newClient(ClientConfig config, ClientListener listener) {
        return WebSocketKit.createClient(config, listener);
    }

    @Override
    protected WebSocketServer newServer(ServerConfig config, ServerListener listener) {
        return WebSocketKit.createServer(config, listener);
    }

    @Override
    protected String backendType() {
        return "javawebsocket";
    }
}
