/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.javawebsocket;

import cn.aifei.websocket.ClientConfig;
import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.Message;
import cn.aifei.websocket.WebSocketConnectionException;
import cn.aifei.websocket.internal.AbstractWebSocketClient;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;

/**
 * 客户端的 Java-WebSocket 实现：把建连、收发、关闭与远端地址桥接到 {@link AbstractWebSocketClient} 的共享状态机。
 *
 * <p>底层客户端对象由内部子类 {@link Transport} 承载，其回调只做「翻译 + 回灌」，状态迁移、发送前置校验与
 * 自动重连编排都由骨架实现。每次 {@link #doConnect()} 都新建传输对象，因为底层客户端对象不可复用；心跳经
 * {@link AbstractWebSocketClient#config()}{@code .heartbeatSeconds()} 映射为底层的连接丢失探测周期。</p>
 */
public final class JavaWebSocketClient extends AbstractWebSocketClient {

    private volatile WebSocketClient transport;

    /**
     * 保存装配输入。
     *
     * @param config 客户端配置
     * @param listener 客户端监听器
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}，或跨字段校验不通过
     */
    public JavaWebSocketClient(ClientConfig config, ClientListener listener) {
        super(config, listener);
    }

    @Override
    protected void doConnect() {
        ClientConfig config = config();
        URI uri = URI.create(config.uri());
        Map<String, String> headers = new LinkedHashMap<>(config.headers());
        Transport client = new Transport(uri, headers, config.connectTimeoutMillis());
        client.setConnectionLostTimeout(config.heartbeatSeconds());
        this.transport = client;
        client.connect();
    }

    @Override
    protected void doSendText(String text) {
        requireTransport().send(text);
    }

    @Override
    protected void doSendBinary(byte[] bytes) {
        requireTransport().send(bytes);
    }

    @Override
    protected void doClose(int code, String reason) {
        WebSocketClient client = this.transport;
        if (client == null) {
            return;
        }
        boolean connecting = client.getReadyState() == ReadyState.NOT_YET_CONNECTED;
        client.close(code, reason == null ? "" : reason);
        if (connecting) {
            // 建连尚未完成时底层 close 不会中止连接线程，关闭底层套接字以尽力中止本次建连
            closeSocketQuietly(client);
        }
    }

    @Override
    protected String doRemoteAddress() {
        WebSocketClient client = this.transport;
        return client == null ? null : JavaWebSocketSupport.formatAddress(client.getRemoteSocketAddress());
    }

    /**
     * 取当前传输对象；仅在发送路径调用，此时状态必为 {@code OPEN}，传输对象必然存在。
     *
     * @return 当前传输对象
     * @throws WebSocketConnectionException 传输对象缺失（理论不可达，作为运行态故障兜底）
     */
    private WebSocketClient requireTransport() {
        WebSocketClient client = this.transport;
        if (client == null) {
            throw new WebSocketConnectionException("web socket transport is not available");
        }
        return client;
    }

    /**
     * 尽力关闭底层套接字以中止建连；关闭失败无需上浮，交由骨架的关闭收敛处理。
     *
     * @param client 传输对象
     */
    private void closeSocketQuietly(WebSocketClient client) {
        Socket socket = client.getSocket();
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // 尽力中止建连，套接字关闭失败不再上浮
        }
    }

    /**
     * 底层客户端子类：把 Java-WebSocket 的回调翻译为本模块骨架的回灌调用。
     */
    private final class Transport extends WebSocketClient {

        private Transport(URI uri, Map<String, String> headers, int connectTimeoutMillis) {
            super(uri, new Draft_6455(), headers, connectTimeoutMillis);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            handleOpen();
        }

        @Override
        public void onMessage(String message) {
            handleMessage(Message.text(message));
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
            handleMessage(Message.binary(JavaWebSocketSupport.toBytes(bytes)));
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            handleClose(code, reason, remote);
        }

        @Override
        public void onError(Exception ex) {
            handleError(ex);
        }
    }
}
