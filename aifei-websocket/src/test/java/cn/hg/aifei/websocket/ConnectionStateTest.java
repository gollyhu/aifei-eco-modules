/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * 连接状态机的合法迁移表与非法迁移处理（设计 §4.6 / §6.1 / §6.4）。
 *
 * <p>状态迁移由 {@code internal.AbstractWebSocketClient} 驱动，本测试经公共 API（{@link WebSocketKit}）在
 * 本地回环上驱动真实迁移，不依赖内部类型。</p>
 */
public class ConnectionStateTest {

    /**
     * 事件等待上限（毫秒）；给足但不长，避免失败用例拖慢构建。
     */
    private static final long TIMEOUT_MILLIS = 5000L;

    /**
     * 状态枚举取值固定且与 RFC6455 / W3C 一致。
     */
    @Test
    public void shouldExposeFourStatesInRfcOrder() {
        assertArrayEquals(
                new ConnectionState[] {
                        ConnectionState.CONNECTING, ConnectionState.OPEN, ConnectionState.CLOSING, ConnectionState.CLOSED},
                ConnectionState.values());
        assertSame(ConnectionState.OPEN, ConnectionState.valueOf("OPEN"));
    }

    /**
     * 新建实例的初始态为 {@code CLOSED}，且未发起任何连接。
     */
    @Test
    public void shouldStartClosed() {
        WebSocketClient client = WebSocketKit.createClient(
                clientConfig("ws://127.0.0.1:1/"), new ClientRecorder());
        try {
            assertSame(ConnectionState.CLOSED, client.state());
            assertFalse(client.isOpen());
            assertNull(client.remoteAddress());
            assertEquals("javawebsocket", client.backendType());
        } finally {
            client.close();
        }
    }

    /**
     * {@code CLOSED → CONNECTING → OPEN}：握手成功后触发 {@code onOpen}。
     */
    @Test
    public void shouldReachOpenAfterConnect() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig(uriOf(server)), listener);
        try {
            client.connect();
            assertTrue("应在超时内建立连接", listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertSame(ConnectionState.OPEN, client.state());
            assertTrue(client.isOpen());
            assertNotNull("已连接时远端地址不应为空", client.remoteAddress());
            assertTrue("远端地址应为回环地址", client.remoteAddress().startsWith("127.0.0.1:"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * {@code OPEN → CLOSING → CLOSED}：用户主动关闭后收敛到 {@code CLOSED} 且 {@code onClose} 恰好一次。
     */
    @Test
    public void shouldReachClosedAfterClose() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig(uriOf(server)), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.close();
            assertTrue("应在超时内关闭", listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitState(client, ConnectionState.CLOSED);
            assertEquals(1, listener.closeCount.get());
            assertFalse(listener.lastCloseRemote);
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * {@code OPEN → CLOSED}：对端关闭时客户端以 {@code remote = true} 收敛到 {@code CLOSED}。
     */
    @Test
    public void shouldReachClosedWhenPeerCloses() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig(uriOf(server)), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            server.stop();
            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitState(client, ConnectionState.CLOSED);
            assertTrue("对端发起关闭时 remote 应为 true", listener.lastCloseRemote);
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * {@code CONNECTING} 期间再次 {@code connect()} 抛 {@link IllegalStateException} 且不改变状态。
     *
     * <p>用只接受 TCP、不回 WebSocket 握手响应的黑洞端点把状态钉在 {@code CONNECTING}，使断言确定性成立。</p>
     */
    @Test
    public void shouldRejectConnectWhileConnecting() throws Exception {
        BlackholeServer blackhole = new BlackholeServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig(blackhole.uri()), listener);
        try {
            client.connect();
            assertSame(ConnectionState.CONNECTING, client.state());
            assertThrows(IllegalStateException.class, client::connect);
            assertSame(ConnectionState.CONNECTING, client.state());
        } finally {
            client.close();
            awaitState(client, ConnectionState.CLOSED);
            blackhole.close();
        }
    }

    /**
     * {@code OPEN} 期间 {@code connect()} 抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectConnectWhileOpen() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig(uriOf(server)), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            assertThrows(IllegalStateException.class, client::connect);
            assertSame(ConnectionState.OPEN, client.state());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 非 {@code OPEN} 时 {@code send*} 抛 {@link IllegalStateException}，且不改变状态。
     */
    @Test
    public void shouldRejectSendWhenNotOpen() {
        WebSocketClient client = WebSocketKit.createClient(
                clientConfig("ws://127.0.0.1:1/"), new ClientRecorder());
        try {
            assertThrows(IllegalStateException.class, () -> client.send("x"));
            assertThrows(IllegalStateException.class, () -> client.send(new byte[] {1}));
            assertThrows(IllegalStateException.class, () -> client.send(new Object()));
            assertSame(ConnectionState.CLOSED, client.state());
        } finally {
            client.close();
        }
    }

    /**
     * {@code CLOSED} 期间 {@code close()} 幂等无操作：不抛异常、不触发 {@code onClose}。
     */
    @Test
    public void shouldTreatCloseAsNoOpWhenClosed() throws Exception {
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig("ws://127.0.0.1:1/"), listener);

        client.close();
        client.close();
        client.close(1000, "again");

        assertSame(ConnectionState.CLOSED, client.state());
        Thread.sleep(200L);
        assertEquals(0, listener.closeCount.get());
    }

    /**
     * {@code close()} 之后允许显式 {@code connect()} 重新建连。
     */
    @Test
    public void shouldAllowConnectAfterClose() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = WebSocketKit.createClient(clientConfig(uriOf(server)), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.close();
            awaitState(client, ConnectionState.CLOSED);

            client.connect();
            awaitOpenCount(listener, 2);
            assertSame(ConnectionState.OPEN, client.state());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 启动一个绑定 {@code 127.0.0.1} 随机端口的服务端。
     *
     * @return 已启动的服务端
     */
    private static WebSocketServer startLoopbackServer() {
        ServerConfig config = new ServerConfig();
        config.setHost("127.0.0.1");
        config.setPort(0);
        WebSocketServer server = WebSocketKit.createServer(config, new NoopServerListener());
        server.start();
        return server;
    }

    /**
     * 构造仅含地址的客户端配置。
     *
     * @param uri 目标地址
     * @return 客户端配置
     */
    private static ClientConfig clientConfig(String uri) {
        ClientConfig config = new ClientConfig();
        config.setUri(uri);
        return config;
    }

    /**
     * 由服务端实际端口构造回环地址。
     *
     * @param server 已启动的服务端
     * @return 目标地址
     */
    private static String uriOf(WebSocketServer server) {
        return "ws://127.0.0.1:" + server.port() + "/";
    }

    /**
     * 轮询等待客户端到达指定状态。
     *
     * @param client 客户端
     * @param expected 期望状态
     * @throws InterruptedException 等待被中断
     */
    private static void awaitState(WebSocketClient client, ConnectionState expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (client.state() == expected) {
                return;
            }
            Thread.sleep(20L);
        }
        assertSame("超时未到达期望状态", expected, client.state());
    }

    /**
     * 轮询等待累计 {@code onOpen} 次数达到期望值。
     *
     * @param listener 客户端回调记录器
     * @param expected 期望次数
     * @throws InterruptedException 等待被中断
     */
    private static void awaitOpenCount(ClientRecorder listener, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (listener.openCount.get() >= expected) {
                return;
            }
            Thread.sleep(20L);
        }
        assertEquals("超时未达到期望的建连次数", expected, listener.openCount.get());
    }

    /**
     * 客户端回调记录器：以同步器等待建连 / 关闭事件，并记录次数与对端标志。
     */
    static final class ClientRecorder implements ClientListener {

        final CountDownLatch openLatch = new CountDownLatch(1);

        final CountDownLatch closeLatch = new CountDownLatch(1);

        final AtomicInteger openCount = new AtomicInteger();

        final AtomicInteger closeCount = new AtomicInteger();

        volatile boolean lastCloseRemote;

        @Override
        public void onOpen() {
            openCount.incrementAndGet();
            openLatch.countDown();
        }

        @Override
        public void onMessage(Message message) {
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCount.incrementAndGet();
            lastCloseRemote = remote;
            closeLatch.countDown();
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    /**
     * 服务端回调空实现：状态机用例只关心客户端侧迁移。
     */
    static final class NoopServerListener implements ServerListener {

        @Override
        public void onOpen(WebSocketSession session) {
        }

        @Override
        public void onMessage(WebSocketSession session, Message message) {
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason, boolean remote) {
        }
    }

    /**
     * TCP 黑洞端点：接受连接但永不回 WebSocket 握手响应，用于把客户端状态稳定钉在 {@code CONNECTING}。
     */
    static final class BlackholeServer {

        private final ServerSocket serverSocket;

        private final List<Socket> accepted = new CopyOnWriteArrayList<>();

        private volatile boolean running = true;

        BlackholeServer() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            Thread acceptor = new Thread(new Runnable() {

                @Override
                public void run() {
                    acceptLoop();
                }
            }, "blackhole-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        String uri() {
            return "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/";
        }

        private void acceptLoop() {
            while (running) {
                try {
                    accepted.add(serverSocket.accept());
                } catch (IOException e) {
                    return;
                }
            }
        }

        void close() {
            running = false;
            closeQuietly(serverSocket);
            for (Socket socket : accepted) {
                closeQuietly(socket);
            }
        }

        private static void closeQuietly(ServerSocket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败不影响用例结论
            }
        }

        private static void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 关闭失败不影响用例结论
            }
        }
    }
}
