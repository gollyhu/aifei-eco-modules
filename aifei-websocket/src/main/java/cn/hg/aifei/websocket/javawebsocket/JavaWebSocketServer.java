/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.javawebsocket;

import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketException;
import cn.hg.aifei.websocket.internal.AbstractWebSocketServer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;

/**
 * 服务端的 Java-WebSocket 实现：包装底层 {@link org.java_websocket.server.WebSocketServer}，把绑定、会话事件与
 * pong 活跃刷新桥接到 {@link AbstractWebSocketServer} 的共享逻辑。
 *
 * <p>底层服务端对象由内部子类 {@link Transport} 承载，其 {@code onStart} / {@code onOpen} / {@code onMessage} /
 * {@code onClose} / {@code onError} / {@code onWebsocketPong} 回调都只做「翻译 + 回灌」，不再重复实现会话登记、
 * 广播与空闲回收。{@code onStart} 仅用于解除 {@link #doStart()} 的等待（保证返回时端口已绑定、{@link #doPort()}
 * 可读实际端口），不在此触发监听器的 {@code onStart}（由骨架负责）。</p>
 */
public final class JavaWebSocketServer extends AbstractWebSocketServer {

    /**
     * 等待底层完成端口绑定的上限（毫秒）；绑定成功或失败都会提前解除等待。
     */
    private static final long START_TIMEOUT_MILLIS = 5000L;

    /**
     * 停机时留给底层向各会话发送关闭帧的时间（毫秒）。
     */
    private static final int STOP_GRACE_MILLIS = 1000;

    /**
     * 底层连接与其本模块会话的对应关系；键为底层连接，值在会话登记前建立、注销时移出。
     */
    private final Map<WebSocket, JavaWebSocketSession> sessionRegistry = new ConcurrentHashMap<>();

    private volatile Transport transport;

    /**
     * 绑定完成的信号；每次 {@link #doStart()} 重新创建。
     */
    private volatile CountDownLatch startLatch;

    /**
     * 绑定阶段的服务端级故障；用于把异步绑定失败上浮为启动失败。
     */
    private volatile Exception startError;

    /**
     * 保存装配输入。
     *
     * @param config 服务端配置
     * @param listener 服务端监听器
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}
     */
    public JavaWebSocketServer(ServerConfig config, ServerListener listener) {
        super(config, listener);
    }

    @Override
    protected void doStart() {
        CountDownLatch latch = new CountDownLatch(1);
        this.startLatch = latch;
        this.startError = null;
        ServerConfig config = config();
        Transport server = new Transport(new InetSocketAddress(config.host(), config.port()));
        server.setReuseAddr(config.reuseAddress());
        if (config.maxPendingConnections() >= 0) {
            server.setMaxPendingConnections(config.maxPendingConnections());
        }
        server.setConnectionLostTimeout(config.heartbeatSeconds());
        this.transport = server;
        server.start();
        awaitBound(latch);
    }

    @Override
    protected void doStop() {
        Transport server = this.transport;
        this.transport = null;
        if (server == null) {
            return;
        }
        try {
            server.stop(STOP_GRACE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebSocketException("interrupted while stopping server", e);
        }
    }

    @Override
    protected int doPort() {
        Transport server = this.transport;
        return server == null ? 0 : server.getPort();
    }

    /**
     * 等待底层完成绑定：绑定成功或失败都会解除等待；超时或失败上浮为启动失败。
     *
     * @param latch 绑定信号
     * @throws WebSocketException 绑定失败、绑定超时或等待被中断
     */
    private void awaitBound(CountDownLatch latch) {
        boolean bound;
        try {
            bound = latch.await(START_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebSocketException("interrupted while starting server", e);
        }
        if (startError != null) {
            throw new WebSocketException("failed to bind server port", startError);
        }
        if (!bound) {
            throw new WebSocketException("timed out while binding server port");
        }
    }

    /**
     * 底层服务端子类：把 Java-WebSocket 的回调翻译为本模块骨架的回灌调用。
     */
    private final class Transport extends org.java_websocket.server.WebSocketServer {

        private Transport(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onStart() {
            CountDownLatch latch = startLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            JavaWebSocketSession session = new JavaWebSocketSession(conn, JavaWebSocketServer.this.config().codec());
            sessionRegistry.put(conn, session);
            handleSessionOpen(session);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            JavaWebSocketSession session = sessionRegistry.get(conn);
            if (session != null) {
                handleSessionMessage(session, Message.text(message));
            }
        }

        @Override
        public void onMessage(WebSocket conn, ByteBuffer blob) {
            JavaWebSocketSession session = sessionRegistry.get(conn);
            if (session != null) {
                handleSessionMessage(session, Message.binary(JavaWebSocketSupport.toBytes(blob)));
            }
        }

        @Override
        public void onWebsocketPong(WebSocket conn, Framedata frame) {
            JavaWebSocketSession session = sessionRegistry.get(conn);
            if (session != null) {
                refreshSessionActivity(session);
            }
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            JavaWebSocketSession session = sessionRegistry.remove(conn);
            if (session != null) {
                handleSessionClose(session, code, reason, remote);
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (conn == null) {
                startError = ex;
                CountDownLatch latch = startLatch;
                if (latch != null) {
                    latch.countDown();
                }
                handleError(null, ex);
                return;
            }
            handleError(sessionRegistry.get(conn), ex);
        }
    }
}
