/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketException;
import cn.hg.aifei.websocket.WebSocketSession;
import cn.hg.aifei.websocket.internal.AbstractWebSocketServer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 服务端的内建 JDK 实现：继承 {@link AbstractWebSocketServer}，只实现启停与端口三个钩子，用 {@link ServerSocket}
 * 接受连接并为每条连接完成握手、登记会话。
 *
 * <p>会话登记 / 注销、广播与空闲回收调度都在共享骨架内实现一次，本类不复制任何规则。底层事件只做「翻译 + 回灌」：
 * 消息经 {@code handleSessionMessage} 回灌，pong 经 {@code refreshSessionActivity} 回灌（使只回 pong 的存活会话
 * 不被空闲回收误判），关闭经 {@code handleSessionClose} 回灌。握手失败的连接按 HTTP {@code 400} 回绝并关闭。
 *
 * <p>本轮<b>仅支持 {@code ws}</b>（不提供证书 / 私钥配置）。本类属内部实现，不是公共 API。</p>
 */
public final class JdkWebSocketServer extends AbstractWebSocketServer {

    /**
     * 未显式配置挂起队列时的缺省积压长度。
     */
    private static final int DEFAULT_BACKLOG = 50;

    /**
     * 服务端 {@code stop()} 向会话发出的关闭码（RFC6455 的 GOING_AWAY）。
     */
    private static final int GOING_AWAY = 1001;

    /**
     * 停止时等待接受线程退出的上限（毫秒）。
     */
    private static final long STOP_JOIN_MILLIS = 1000L;

    /**
     * 流缓冲区大小（字节）。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 监听套接字；未启动时为 {@code null}。
     */
    private ServerSocket serverSocket;

    /**
     * 接受连接的线程；未启动时为 {@code null}。
     */
    private Thread acceptThread;

    /**
     * 是否仍在接受新连接。
     */
    private volatile boolean running;

    /**
     * 保存装配输入。
     *
     * @param config 服务端配置
     * @param listener 服务端监听器
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}
     */
    public JdkWebSocketServer(ServerConfig config, ServerListener listener) {
        super(config, listener);
    }

    @Override
    protected void doStart() {
        ServerConfig config = config();
        try {
            ServerSocket created = new ServerSocket();
            created.setReuseAddress(config.reuseAddress());
            int backlog = config.maxPendingConnections() >= 0 ? config.maxPendingConnections() : DEFAULT_BACKLOG;
            created.bind(new InetSocketAddress(config.host(), config.port()), backlog);
            this.serverSocket = created;
        } catch (IOException e) {
            throw new WebSocketException("failed to bind server port", e);
        }
        running = true;
        Thread acceptor = new Thread(new Runnable() {

            @Override
            public void run() {
                acceptLoop();
            }
        }, "aifei-websocket-accept");
        acceptor.setDaemon(true);
        this.acceptThread = acceptor;
        acceptor.start();
    }

    @Override
    protected void doStop() {
        running = false;
        JdkSocketSupport.closeQuietly(serverSocket);
        this.serverSocket = null;
        Thread acceptor = this.acceptThread;
        this.acceptThread = null;
        if (acceptor != null) {
            try {
                acceptor.join(STOP_JOIN_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new WebSocketException("interrupted while stopping server", e);
            }
        }
        for (WebSocketSession session : sessions()) {
            try {
                session.close(GOING_AWAY, "server going away");
            } catch (RuntimeException ignored) {
                // 会话可能已自行关闭，停止流程静默跳过
            }
        }
    }

    @Override
    protected int doPort() {
        ServerSocket current = this.serverSocket;
        return current == null ? 0 : current.getLocalPort();
    }

    // ─────────── 内部实现 ───────────

    /**
     * 接受循环：每接受一条连接就交给独立线程完成握手与后续读循环，避免慢握手阻塞接受。
     */
    private void acceptLoop() {
        ServerSocket current = this.serverSocket;
        while (running && current != null) {
            Socket accepted;
            try {
                accepted = current.accept();
            } catch (IOException e) {
                if (running) {
                    handleError(null, e);
                }
                return;
            }
            if (!running) {
                JdkSocketSupport.closeQuietly(accepted);
                return;
            }
            Thread initializer = new Thread(new ConnectionInitializer(accepted),
                    "aifei-websocket-connection-" + accepted.getPort());
            initializer.setDaemon(true);
            initializer.start();
        }
    }

    /**
     * 完成一条连接的握手、会话登记与读循环启动；任一环节失败都回绝该连接并关闭套接字。
     */
    private final class ConnectionInitializer implements Runnable {

        private final Socket socket;

        private ConnectionInitializer(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            initialize();
        }

        /**
         * 握手 → 建连接对象 → 建会话 → 登记 → 启动读循环。
         */
        private void initialize() {
            InputStream in = null;
            OutputStream out = null;
            try {
                socket.setTcpNoDelay(true);
                in = new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE);
                out = new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE);
                Rfc6455Handshake.Request request = Rfc6455Handshake.readRequest(in);
                String key = Rfc6455Handshake.requireKey(request);
                Rfc6455Handshake.writeResponse(out, Rfc6455Handshake.acceptFor(key));
                out.flush();

                final AtomicReference<JdkWebSocketSession> holder = new AtomicReference<JdkWebSocketSession>();
                JdkWebSocketConnection connection = new JdkWebSocketConnection(socket,
                        config().heartbeatSeconds(), new SessionCallback(holder));
                JdkWebSocketSession session = new JdkWebSocketSession(connection, config().codec());
                holder.set(session);
                handleSessionOpen(session);
                connection.beginReading();
            } catch (IOException e) {
                if (out != null) {
                    try {
                        Rfc6455Handshake.writeBadRequest(out);
                        out.flush();
                    } catch (IOException ignored) {
                        // 回绝响应尽力写出，写失败直接关闭连接
                    }
                }
                handleError(null, e);
                JdkSocketSupport.closeQuietly(socket);
            } catch (RuntimeException e) {
                handleError(null, e);
                JdkSocketSupport.closeQuietly(socket);
            }
        }
    }

    /**
     * 传输事件回灌：把 {@link JdkWebSocketConnection} 的事件翻译为共享骨架的会话级回灌调用。
     */
    private final class SessionCallback implements JdkWebSocketConnection.Callback {

        private final AtomicReference<JdkWebSocketSession> holder;

        private SessionCallback(AtomicReference<JdkWebSocketSession> holder) {
            this.holder = holder;
        }

        @Override
        public void onOpen() {
            // 服务端角色的 onOpen 由骨架在登记会话时触发，此处不重复触发
        }

        @Override
        public void onMessage(Message message) {
            JdkWebSocketSession session = holder.get();
            if (session != null) {
                handleSessionMessage(session, message);
            }
        }

        @Override
        public void onPong() {
            JdkWebSocketSession session = holder.get();
            if (session != null) {
                refreshSessionActivity(session);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            JdkWebSocketSession session = holder.get();
            if (session != null) {
                handleSessionClose(session, code, reason, remote);
            }
        }

        @Override
        public void onError(Throwable error) {
            handleError(holder.get(), error);
        }
    }
}