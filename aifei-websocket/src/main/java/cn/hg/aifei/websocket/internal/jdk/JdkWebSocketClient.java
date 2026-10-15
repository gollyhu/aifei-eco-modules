/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketConnectionException;
import cn.hg.aifei.websocket.internal.AbstractWebSocketClient;

/**
 * 客户端的内建 JDK 实现：继承 {@link AbstractWebSocketClient}，只实现五个传输钩子，把建连、收发、关闭与远端
 * 地址桥接到 {@link JdkWebSocketConnection}。
 *
 * <p>状态迁移、发送前置校验、编解码路径与自动重连编排都在共享骨架内实现一次，本类不复制任何规则。底层回调只做
 * 「翻译 + 回灌」：{@code onOpen} / {@code onMessage} / {@code onClose} / {@code onError} 分别对应骨架的
 * {@link #handleOpen()} / {@link #handleMessage(Message)} / {@code handleClose} / {@link #handleError(Throwable)}。
 * 每次 {@link #doConnect()} 都新建连接对象，因为连接对象不可复用。本类属内部实现，不是公共 API。</p>
 */
public final class JdkWebSocketClient extends AbstractWebSocketClient {

    /**
     * 当前连接对象；每次建连都替换为新实例。
     */
    private volatile JdkWebSocketConnection connection;

    /**
     * 保存装配输入。
     *
     * @param config 客户端配置
     * @param listener 客户端监听器
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}，或跨字段校验不通过
     */
    public JdkWebSocketClient(ClientConfig config, ClientListener listener) {
        super(config, listener);
    }

    @Override
    protected void doConnect() {
        ClientConfig config = config();
        JdkWebSocketConnection created = new JdkWebSocketConnection(config.uri(), config.headers(),
                config.connectTimeoutMillis(), config.heartbeatSeconds(), new TransportCallback());
        this.connection = created;
        created.connectAsync();
    }

    @Override
    protected void doSendText(String text) {
        requireConnection().sendText(text);
    }

    @Override
    protected void doSendBinary(byte[] bytes) {
        requireConnection().sendBinary(bytes);
    }

    @Override
    protected void doClose(int code, String reason) {
        JdkWebSocketConnection current = this.connection;
        if (current == null) {
            return;
        }
        current.close(code, reason);
    }

    @Override
    protected String doRemoteAddress() {
        JdkWebSocketConnection current = this.connection;
        return current == null ? null : current.remoteAddress();
    }

    /**
     * 取当前连接对象；仅在发送路径调用，此时状态必为 {@code OPEN}，连接对象必然存在。
     *
     * @return 当前连接对象
     * @throws WebSocketConnectionException 连接对象缺失（理论不可达，作为运行态故障兜底）
     */
    private JdkWebSocketConnection requireConnection() {
        JdkWebSocketConnection current = this.connection;
        if (current == null) {
            throw new WebSocketConnectionException("web socket transport is not available");
        }
        return current;
    }

    /**
     * 传输事件回灌：把 {@link JdkWebSocketConnection} 的事件翻译为共享骨架的回灌调用。
     */
    private final class TransportCallback implements JdkWebSocketConnection.Callback {

        @Override
        public void onOpen() {
            handleOpen();
        }

        @Override
        public void onMessage(Message message) {
            handleMessage(message);
        }

        @Override
        public void onPong() {
            // 客户端侧探活由连接内部完成，pong 不需要回灌到骨架
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            handleClose(code, reason, remote);
        }

        @Override
        public void onError(Throwable error) {
            handleError(error);
        }
    }
}
