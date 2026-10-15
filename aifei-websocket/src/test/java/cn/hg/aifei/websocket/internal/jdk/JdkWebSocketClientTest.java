/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientContractTest;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketKit;
import cn.hg.aifei.websocket.WebSocketServer;

/**
 * 内建 JDK 传输的客户端契约测试：继承 {@link ClientContractTest} 并提供本实现的实例工厂。
 *
 * <p>经 {@link WebSocketKit} 构建（与生产装配走同一条路径，都是直接 {@code new} JDK 实现）；断言全部来自契约
 * 基类，本类只需保证实例由内建实现产出。</p>
 */
public class JdkWebSocketClientTest extends ClientContractTest {

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
        return "jdk";
    }
}
