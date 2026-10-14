/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.websocket.spi.MessageCodec;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;

/**
 * 客户端接口的抽象契约测试基类：{@link WebSocketClient} 与 {@link ClientListener} 的全部公共行为只在此断言一次，
 * 各后端实现继承运行，保证多实现行为一致（对应设计 §6.1 / §6.3 / §6.5 / §6.7）。
 *
 * <p>子类提供两端的实例工厂：客户端契约需要真实对端才能覆盖「OPEN 后行为 / 断线重连 / onClose 恰好一次」等
 * 用例，而同一后端天然同时提供 client 与 server，故两个工厂都交由子类实现。</p>
 *
 * <p>本类只使用公共 API 与 JDK 类型，不引用任何内部类型与第三方类型。</p>
 */
public abstract class ClientContractTest {

    /**
     * 事件等待上限（毫秒）。
     */
    protected static final long TIMEOUT_MILLIS = 5000L;

    /**
     * 构造被测客户端的实例。
     *
     * @param config 客户端配置
     * @param listener 客户端回调
     * @return 客户端实例
     */
    protected abstract WebSocketClient newClient(ClientConfig config, ClientListener listener);

    /**
     * 构造供真实对端使用的服务端实例。
     *
     * @param config 服务端配置
     * @param listener 服务端回调
     * @return 服务端实例
     */
    protected abstract WebSocketServer newServer(ServerConfig config, ServerListener listener);

    /**
     * 返回被测后端的类型标识，用于断言 {@code backendType()}。
     *
     * @return 后端类型，如 {@code "javawebsocket"}
     */
    protected abstract String backendType();

    /**
     * 初始态为 {@code CLOSED}，未连接时远端地址为 {@code null}，后端类型自述正确。
     */
    @Test
    public void shouldStartClosedWithoutConnection() {
        WebSocketClient client = newClient(clientConfig("ws://127.0.0.1:1/"), new ClientRecorder());
        try {
            assertSame(ConnectionState.CLOSED, client.state());
            assertFalse(client.isOpen());
            assertNull(client.remoteAddress());
            assertEquals(backendType(), client.backendType());
        } finally {
            client.close();
        }
    }

    /**
     * 非空参数传 {@code null} 一律抛 {@link IllegalArgumentException}（先于状态判定）。
     */
    @Test
    public void shouldRejectNullSendArguments() {
        WebSocketClient client = newClient(clientConfig("ws://127.0.0.1:1/"), new ClientRecorder());
        try {
            assertThrows(IllegalArgumentException.class, () -> client.send((String) null));
            assertThrows(IllegalArgumentException.class, () -> client.send((byte[]) null));
            assertThrows(IllegalArgumentException.class, () -> client.send((Object) null));
        } finally {
            client.close();
        }
    }

    /**
     * 非 {@code OPEN} 时 {@code send*} 抛 {@link IllegalStateException}，消息被丢弃、不排队。
     */
    @Test
    public void shouldRejectSendWhenNotOpen() {
        WebSocketClient client = newClient(clientConfig("ws://127.0.0.1:1/"), new ClientRecorder());
        try {
            assertThrows(IllegalStateException.class, () -> client.send("dropped"));
            assertThrows(IllegalStateException.class, () -> client.send(new byte[] {1}));
            assertThrows(IllegalStateException.class, () -> client.send(new Object()));
            assertSame(ConnectionState.CLOSED, client.state());
        } finally {
            client.close();
        }
    }

    /**
     * 关闭码越界抛 {@link IllegalArgumentException}，不改变状态。
     */
    @Test
    public void shouldRejectOutOfRangeCloseCode() {
        WebSocketClient client = newClient(clientConfig("ws://127.0.0.1:1/"), new ClientRecorder());
        try {
            assertThrows(IllegalArgumentException.class, () -> client.close(999, "too low"));
            assertThrows(IllegalArgumentException.class, () -> client.close(5000, "too high"));
            assertSame(ConnectionState.CLOSED, client.state());
        } finally {
            client.close();
        }
    }

    /**
     * {@code CONNECTING} 期间重复 {@code connect()} 抛 {@link IllegalStateException}。
     *
     * <p>用只接受 TCP、不回 WebSocket 握手响应的黑洞端点把状态钉在 {@code CONNECTING}，使断言确定性成立。</p>
     */
    @Test
    public void shouldRejectRepeatedConnectWhileConnecting() throws Exception {
        BlackholeServer blackhole = new BlackholeServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = newClient(clientConfig(blackhole.uri()), listener);
        try {
            client.connect();
            assertSame(ConnectionState.CONNECTING, client.state());
            assertThrows(IllegalStateException.class, client::connect);
            assertSame(ConnectionState.CONNECTING, client.state());
        } finally {
            client.close();
            awaitCondition(() -> client.state() == ConnectionState.CLOSED, TIMEOUT_MILLIS,
                    "close() 应中止建连并回到 CLOSED");
            blackhole.close();
        }
    }

    /**
     * {@code CLOSED} 期间 {@code close()} 幂等无操作：不抛异常、不触发 {@code onClose}。
     */
    @Test
    public void shouldTreatCloseAsNoOpWhenClosed() throws Exception {
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = newClient(clientConfig("ws://127.0.0.1:1/"), listener);

        client.close();
        client.close();
        client.close(1000, "again");

        assertSame(ConnectionState.CLOSED, client.state());
        sleepQuietly(200L);
        assertEquals(0, listener.closeCount.get());
    }

    /**
     * 未配置编解码器却在 {@code OPEN} 时调 {@code send(Object)} 抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectObjectSendWithoutCodec() throws Exception {
        ServerRecorder serverListener = new ServerRecorder();
        WebSocketServer server = newServer(loopbackServerConfig(), serverListener);
        WebSocketClient client = null;
        try {
            server.start();
            ClientRecorder listener = new ClientRecorder();
            WebSocketClient created = newClient(clientConfig(uriOf(server)), listener);
            client = created;
            created.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            assertThrows(IllegalStateException.class, () -> created.send(new Object()));
            assertSame(ConnectionState.OPEN, created.state());
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 配置了编解码器后，{@code send(Object)} 经编解码器编码后送达对端。
     */
    @Test
    public void shouldSendObjectThroughConfiguredCodec() throws Exception {
        ServerRecorder serverListener = new ServerRecorder();
        WebSocketServer server = newServer(loopbackServerConfig(), serverListener);
        WebSocketClient client = null;
        try {
            server.start();
            ClientRecorder listener = new ClientRecorder();
            ClientConfig config = clientConfig(uriOf(server));
            config.setCodec(new TextCodec());
            client = newClient(config, listener);
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.send("hello-codec");

            Message received = serverListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("对端应收到编解码后的消息", received);
            assertTrue(received.isText());
            assertEquals("hello-codec", received.asText());
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 用户主动 {@code close()} 后 {@code onClose} 恰好一次，且 {@code remote} 为 {@code false}。
     */
    @Test
    public void shouldFireOnCloseExactlyOnceOnUserClose() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = newClient(clientConfig(uriOf(server)), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.close();
            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertSame(ConnectionState.CLOSED, client.state());
            assertEquals(1, listener.closeCount.get());
            assertFalse(listener.lastCloseRemote);

            sleepQuietly(400L);
            assertEquals("onClose 必须恰好一次", 1, listener.closeCount.get());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 用户主动 {@code close()} 后永不触发自动重连（硬约束）。
     */
    @Test
    public void shouldNotReconnectAfterUserClose() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = newClient(reconnectConfig(uriOf(server), 5), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.close();
            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            // 等待数倍重试延迟，确认未发生任何重连
            sleepQuietly(700L);
            assertEquals(0, listener.reconnectCount.get());
            assertEquals(1, listener.openCount.get());
            assertEquals(1, listener.closeCount.get());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 对端持续不可达时自动重连按 {@code maxRetries} 耗尽，触发一次 {@code onReconnectExhausted} 并进入终态。
     */
    @Test
    public void shouldReconnectAndExhaustWhenPeerStaysDown() throws Exception {
        WebSocketServer server = startLoopbackServer();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = newClient(reconnectConfig(uriOf(server), 2), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            server.stop();

            awaitCondition(() -> listener.exhaustedCount.get() == 1, TIMEOUT_MILLIS,
                    "应在超时内进入重连耗尽终态");
            assertEquals(2, listener.reconnectCount.get());
            assertEquals(2, listener.lastExhaustedAttempts);
            assertNotNull("耗尽时应携带最后一次错误", listener.lastError.get());
            assertSame(ConnectionState.CLOSED, client.state());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 重连成功（{@code onOpen}）后重试计数复位：再次断线时重连尝试从 {@code 1} 重新开始。
     */
    @Test
    public void shouldResetReconnectAttemptAfterSuccessfulReopen() throws Exception {
        WebSocketServer first = startLoopbackServerOn(0);
        int port = first.port();
        ClientRecorder listener = new ClientRecorder();
        WebSocketClient client = newClient(reconnectConfig(uriOfPort(port), 10), listener);
        WebSocketServer second = null;
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            first.stop();
            awaitCondition(() -> listener.reconnectAttempts().size() >= 2, TIMEOUT_MILLIS,
                    "断线后应至少观察到两次失败的重连尝试");
            List<Integer> failedAttempts = listener.reconnectAttempts();
            assertTrue("重试计数应严格递增", failedAttempts.get(1) > failedAttempts.get(0));

            second = startLoopbackServerOn(port);
            awaitCondition(() -> listener.openCount.get() >= 2, TIMEOUT_MILLIS, "重连应在超时内成功");

            listener.clearReconnectObservations();
            second.stop();

            awaitCondition(() -> !listener.reconnectAttempts().isEmpty(), TIMEOUT_MILLIS,
                    "再次断线后应触发重连");
            assertEquals("重连成功后 attempt 应复位为 1", 1, listener.reconnectAttempts().get(0).intValue());
        } finally {
            client.close();
            first.stop();
            if (second != null) {
                second.stop();
            }
        }
    }

    /**
     * 监听器回调内抛出的异常被回调保护捕获并转投 {@code onError}，且不中断传输线程（后续回调不受影响）。
     */
    @Test
    public void shouldConvertListenerExceptionToOnError() throws Exception {
        ServerRecorder serverListener = new ServerRecorder();
        WebSocketServer server = newServer(loopbackServerConfig(), serverListener);
        ThrowingOnOpenListener listener = new ThrowingOnOpenListener();
        WebSocketClient client = null;
        try {
            server.start();
            client = newClient(clientConfig(uriOf(server)), listener);
            client.connect();

            assertTrue("回调异常应被转投 onError", listener.errorLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertTrue(listener.lastError.get() instanceof IllegalStateException);
            assertEquals("listener-onOpen-boom", listener.lastError.get().getMessage());

            // 传输线程未被污染：对端广播仍能触发后续 onMessage
            assertTrue(serverListener.sessionLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            server.broadcast("after-listener-error");

            assertTrue("后续回调不应受影响", listener.messageLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals("after-listener-error", listener.lastMessage.get().asText());
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 构造仅含地址的客户端配置。
     *
     * @param uri 目标地址
     * @return 客户端配置
     */
    protected static ClientConfig clientConfig(String uri) {
        ClientConfig config = new ClientConfig();
        config.setUri(uri);
        return config;
    }

    /**
     * 构造开启自动重连的客户端配置；抖动置零以便确定性断言重试时机。
     *
     * @param uri 目标地址
     * @param maxRetries 连续失败重试上限
     * @return 客户端配置
     */
    protected static ClientConfig reconnectConfig(String uri, int maxRetries) {
        ClientConfig config = new ClientConfig();
        config.setUri(uri);
        config.setAutoReconnect(true);
        config.setMaxRetries(maxRetries);
        config.setInitialRetryDelayMillis(100L);
        config.setMaxRetryDelayMillis(100L);
        config.setRetryJitterRatio(0.0);
        return config;
    }

    /**
     * 构造绑定 {@code 127.0.0.1} 的服务端配置（端口由调用方指定，{@code 0} 表示随机）。
     *
     * @return 服务端配置
     */
    protected static ServerConfig loopbackServerConfig() {
        return loopbackServerConfig(0);
    }

    /**
     * 构造绑定 {@code 127.0.0.1} 的服务端配置。
     *
     * @param port 监听端口
     * @return 服务端配置
     */
    protected static ServerConfig loopbackServerConfig(int port) {
        ServerConfig config = new ServerConfig();
        config.setHost("127.0.0.1");
        config.setPort(port);
        return config;
    }

    /**
     * 启动一个随机端口的回环服务端。
     *
     * @return 已启动的服务端
     */
    protected WebSocketServer startLoopbackServer() {
        return startLoopbackServerOn(0);
    }

    /**
     * 在指定端口启动回环服务端；用于「同端口重启验证重连成功」的用例。
     *
     * @param port 监听端口；{@code 0} 表示随机
     * @return 已启动的服务端
     */
    protected WebSocketServer startLoopbackServerOn(int port) {
        WebSocketServer server = newServer(loopbackServerConfig(port), new ServerRecorder());
        server.start();
        return server;
    }

    /**
     * 由服务端实际端口构造回环地址。
     *
     * @param server 已启动的服务端
     * @return 目标地址
     */
    protected static String uriOf(WebSocketServer server) {
        return uriOfPort(server.port());
    }

    /**
     * 由端口构造回环地址。
     *
     * @param port 端口
     * @return 目标地址
     */
    protected static String uriOfPort(int port) {
        return "ws://127.0.0.1:" + port + "/";
    }

    /**
     * 轮询等待条件成立，超时即失败并给出消息。
     *
     * @param condition 条件
     * @param timeoutMillis 超时（毫秒）
     * @param message 失败消息
     * @throws InterruptedException 等待被中断
     */
    protected static void awaitCondition(BooleanSupplier condition, long timeoutMillis, String message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20L);
        }
        assertTrue(message, condition.getAsBoolean());
    }

    /**
     * 安静休眠指定的毫秒数。
     *
     * @param millis 休眠毫秒数
     * @throws InterruptedException 等待被中断
     */
    protected static void sleepQuietly(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * 客户端回调记录器：以同步器等待事件，并记录次数、重连尝试与最近错误，供契约断言使用。
     */
    protected static final class ClientRecorder implements ClientListener {

        final CountDownLatch openLatch = new CountDownLatch(1);

        final CountDownLatch closeLatch = new CountDownLatch(1);

        final AtomicInteger openCount = new AtomicInteger();

        final AtomicInteger closeCount = new AtomicInteger();

        final AtomicInteger reconnectCount = new AtomicInteger();

        final AtomicInteger exhaustedCount = new AtomicInteger();

        final AtomicReference<Throwable> lastError = new AtomicReference<>();

        private final List<Integer> attempts = Collections.synchronizedList(new ArrayList<Integer>());

        volatile boolean lastCloseRemote;

        volatile int lastExhaustedAttempts;

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
            lastError.set(error);
        }

        @Override
        public void onReconnect(int attempt, long delayMillis) {
            attempts.add(Integer.valueOf(attempt));
            reconnectCount.incrementAndGet();
        }

        @Override
        public void onReconnectExhausted(int attempts, Throwable cause) {
            lastExhaustedAttempts = attempts;
            exhaustedCount.incrementAndGet();
        }

        /**
         * 返回已观察到的重连尝试次数快照。
         *
         * @return 重连尝试次数列表
         */
        List<Integer> reconnectAttempts() {
            synchronized (attempts) {
                return new ArrayList<Integer>(attempts);
            }
        }

        /**
         * 清空已观察到的重连尝试，用于「成功后再次断线」的分段断言。
         */
        void clearReconnectObservations() {
            attempts.clear();
        }
    }

    /**
     * 服务端回调记录器：等待会话登记并收集收到的消息。
     */
    protected static final class ServerRecorder implements ServerListener {

        final CountDownLatch sessionLatch = new CountDownLatch(1);

        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocketSession session) {
            sessionLatch.countDown();
        }

        @Override
        public void onMessage(WebSocketSession session, Message message) {
            messages.add(message);
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason, boolean remote) {
        }
    }

    /**
     * 文本编解码器测试桩：把业务对象按 {@code String.valueOf} 编码为文本消息。
     */
    protected static final class TextCodec implements MessageCodec {

        @Override
        public Message encode(Object payload) {
            if (payload == null) {
                throw new IllegalArgumentException("payload can not be null");
            }
            return Message.text(String.valueOf(payload));
        }

        @Override
        public <T> T decode(Message message, Class<T> type) {
            if (message == null || type == null) {
                throw new IllegalArgumentException("message and type can not be null");
            }
            return type.cast(message.asText());
        }
    }

    /**
     * 在 {@code onOpen} 内抛异常的监听器：验证回调保护把异常转投 {@code onError}。
     */
    protected static final class ThrowingOnOpenListener implements ClientListener {

        final CountDownLatch errorLatch = new CountDownLatch(1);

        final CountDownLatch messageLatch = new CountDownLatch(1);

        final AtomicReference<Throwable> lastError = new AtomicReference<>();

        final AtomicReference<Message> lastMessage = new AtomicReference<>();

        @Override
        public void onOpen() {
            throw new IllegalStateException("listener-onOpen-boom");
        }

        @Override
        public void onMessage(Message message) {
            lastMessage.set(message);
            messageLatch.countDown();
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
        }

        @Override
        public void onError(Throwable error) {
            lastError.set(error);
            errorLatch.countDown();
        }
    }

    /**
     * TCP 黑洞端点：接受连接但永不回 WebSocket 握手响应，用于把客户端状态稳定钉在 {@code CONNECTING}。
     */
    protected static final class BlackholeServer {

        private final ServerSocket serverSocket;

        private final List<Socket> accepted = Collections.synchronizedList(new ArrayList<Socket>());

        private volatile boolean running = true;

        BlackholeServer() throws Exception {
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
                } catch (Exception e) {
                    return;
                }
            }
        }

        void close() {
            running = false;
            closeQuietly(serverSocket);
            synchronized (accepted) {
                for (Socket socket : accepted) {
                    closeQuietly(socket);
                }
            }
        }

        private static void closeQuietly(ServerSocket socket) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // 关闭失败不影响用例结论
            }
        }

        private static void closeQuietly(Socket socket) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // 关闭失败不影响用例结论
            }
        }
    }
}
