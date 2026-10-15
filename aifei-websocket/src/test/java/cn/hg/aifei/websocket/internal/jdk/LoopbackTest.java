/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.ConnectionState;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketKit;
import cn.hg.aifei.websocket.WebSocketServer;
import cn.hg.aifei.websocket.WebSocketSession;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;

/**
 * 本地回环端到端：只绑定 / 连接 {@code 127.0.0.1}，不联网、不连数据库（设计 §6.1 / §6.5 / §6.3 / §6.6）。
 *
 * <p>覆盖建连、文本与二进制收发、服务端广播、用户主动关闭、断线自动重连成功、重连耗尽，以及设计 §6.2 / §6.6
 * 要求<b>实证</b>的一条：服务端开启心跳、客户端只回 pong 不发数据帧时，会话在超过空闲阈值后仍未被回收。</p>
 */
public class LoopbackTest {

    /**
     * 事件等待上限（毫秒）。
     */
    private static final long TIMEOUT_MILLIS = 5000L;

    /**
     * 建连后客户端处于 {@code OPEN}、远端地址可读。
     */
    @Test
    public void shouldConnectOverLoopback() throws Exception {
        WebSocketServer server = startServer(serverConfig());
        RecordingClient clientListener = new RecordingClient();
        WebSocketClient client = newClient(clientConfig(uri(server.port())), clientListener);
        try {
            client.connect();
            assertTrue(clientListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertSame(ConnectionState.OPEN, client.state());
            assertTrue(client.isOpen());
            assertNotNull(client.remoteAddress());
            assertTrue(client.remoteAddress().startsWith("127.0.0.1:"));
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 文本消息双向收发：客户端发送、服务端回声、客户端收到同一内容。
     */
    @Test
    public void shouldExchangeTextMessages() throws Exception {
        EchoServerListener serverListener = new EchoServerListener();
        WebSocketServer server = startServer(serverConfig(), serverListener);
        RecordingClient clientListener = new RecordingClient();
        WebSocketClient client = newClient(clientConfig(uri(server.port())), clientListener);
        try {
            client.connect();
            assertTrue(clientListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.send("echo-text");

            Message echoed = clientListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("客户端应收到回声", echoed);
            assertTrue(echoed.isText());
            assertEquals("echo-text", echoed.asText());

            Message received = serverListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("服务端应收到原始消息", received);
            assertEquals("echo-text", received.asText());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 二进制消息双向收发：载荷逐字节一致。
     */
    @Test
    public void shouldExchangeBinaryMessages() throws Exception {
        EchoServerListener serverListener = new EchoServerListener();
        WebSocketServer server = startServer(serverConfig(), serverListener);
        RecordingClient clientListener = new RecordingClient();
        WebSocketClient client = newClient(clientConfig(uri(server.port())), clientListener);
        try {
            client.connect();
            assertTrue(clientListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            byte[] payload = new byte[] {0, 1, 2, -1, 127, -128};
            client.send(payload);

            Message echoed = clientListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(echoed);
            assertFalse(echoed.isText());
            assertArrayEquals(payload, echoed.asBytes());

            Message received = serverListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(received);
            assertArrayEquals(payload, received.asBytes());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 服务端广播送达全部已登记会话。
     */
    @Test
    public void shouldBroadcastToAllSessions() throws Exception {
        WebSocketServer server = startServer(serverConfig());
        RecordingClient firstListener = new RecordingClient();
        RecordingClient secondListener = new RecordingClient();
        WebSocketClient first = newClient(clientConfig(uri(server.port())), firstListener);
        WebSocketClient second = newClient(clientConfig(uri(server.port())), secondListener);
        try {
            first.connect();
            second.connect();
            assertTrue(firstListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertTrue(secondListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitCondition(() -> server.sessions().size() == 2, TIMEOUT_MILLIS, "应登记两个会话");

            server.broadcast("fan-out");

            assertEquals("fan-out", firstListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).asText());
            assertEquals("fan-out", secondListener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).asText());
        } finally {
            first.close();
            second.close();
            server.stop();
        }
    }

    /**
     * 用户主动关闭：客户端收敛到 {@code CLOSED} 且 {@code onClose} 恰好一次、{@code remote} 为 {@code false}。
     */
    @Test
    public void shouldFireCloseOnceOnUserClose() throws Exception {
        WebSocketServer server = startServer(serverConfig());
        RecordingClient clientListener = new RecordingClient();
        WebSocketClient client = newClient(clientConfig(uri(server.port())), clientListener);
        try {
            client.connect();
            assertTrue(clientListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            client.close();
            assertTrue(clientListener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertSame(ConnectionState.CLOSED, client.state());
            assertFalse(clientListener.lastCloseRemote);

            Thread.sleep(500L);
            assertEquals("onClose 必须恰好一次", 1, clientListener.closeCount.get());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 服务端 {@code stop()} 以 {@code 1001}（GOING_AWAY）关闭在册会话，客户端 {@code onClose} 应收到该关闭码。
     */
    @Test
    public void shouldReportGoingAwayWhenServerStops() throws Exception {
        WebSocketServer server = startServer(serverConfig());
        RecordingClient clientListener = new RecordingClient();
        WebSocketClient client = newClient(clientConfig(uri(server.port())), clientListener);
        try {
            client.connect();
            assertTrue(clientListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            server.stop();
            assertTrue(clientListener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals("服务端停止应下 GOING_AWAY 关闭码", 1001, clientListener.lastCloseCode);
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * 断线后自动重连成功：对端在同端口重启后，客户端重新建连并触发 {@code onOpen}。
     */
    @Test
    public void shouldReconnectAfterServerRestart() throws Exception {
        WebSocketServer first = startServer(serverConfig());
        int port = first.port();
        RecordingClient listener = new RecordingClient();
        WebSocketClient client = newClient(reconnectConfig(uri(port), 10), listener);
        WebSocketServer second = null;
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            first.stop();
            awaitCondition(() -> listener.reconnectCount.get() >= 1, TIMEOUT_MILLIS, "断线后应触发自动重连");

            second = startServer(serverConfig(port));
            awaitCondition(() -> listener.openCount.get() >= 2, TIMEOUT_MILLIS, "重连应在超时内成功");
            assertTrue(client.isOpen());
        } finally {
            client.close();
            first.stop();
            if (second != null) {
                second.stop();
            }
        }
    }

    /**
     * 对端持续不可达时重连耗尽：进入终态 {@code CLOSED} 并触发一次 {@code onReconnectExhausted}。
     */
    @Test
    public void shouldExhaustReconnectWhenServerStaysDown() throws Exception {
        WebSocketServer server = startServer(serverConfig());
        RecordingClient listener = new RecordingClient();
        WebSocketClient client = newClient(reconnectConfig(uri(server.port()), 2), listener);
        try {
            client.connect();
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            server.stop();

            awaitCondition(() -> listener.exhaustedCount.get() == 1, TIMEOUT_MILLIS, "应进入重连耗尽终态");
            assertEquals(2, listener.lastExhaustedAttempts);
            assertSame(ConnectionState.CLOSED, client.state());
        } finally {
            client.close();
            server.stop();
        }
    }

    /**
     * pong 可观测性实证（设计 §6.2 / §6.6）：服务端开启心跳与空闲回收，客户端只自动回 pong、不发任何数据帧，
     * 会话在显著超过空闲阈值后仍未被回收。
     *
     * <p>阈值取 {@code 2} 秒、最坏回收延迟约 {@code 2 × 阈值 = 4} 秒，本用例持续观察 {@code 5} 秒：若 pong 未计入
     * 活跃时间，会话会在首次扫描（约 2 秒）即被回收，此处必然失败；因此该用例同时是 pong 结论的实证与守卫。</p>
     */
    @Test
    public void shouldKeepSessionThatOnlyAnswersPongs() throws Exception {
        ServerConfig serverConfig = serverConfig();
        serverConfig.setHeartbeatSeconds(1);
        serverConfig.setSessionIdleTimeoutSeconds(2);
        WebSocketServer server = startServer(serverConfig);
        RecordingClient clientListener = new RecordingClient();
        WebSocketClient client = newClient(clientConfig(uri(server.port())), clientListener);
        try {
            client.connect();
            assertTrue(clientListener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitCondition(() -> server.sessions().size() == 1, TIMEOUT_MILLIS, "应登记一个会话");

            // 客户端不发任何数据帧，仅由传输层自动应答服务端 ping
            assertStays(() -> server.sessions().size() == 1, 5000L, "只回 pong 的存活会话不应被空闲回收");

            assertTrue("客户端连接应保持 OPEN", client.isOpen());
            WebSocketSession session = server.sessions().iterator().next();
            assertTrue("会话应保持 OPEN", session.isOpen());
        } finally {
            client.close();
            server.stop();
        }
    }

    // ─────────── 构造与等待工具 ───────────

    /**
     * 构造绑定 {@code 127.0.0.1} 随机端口的服务端配置。
     *
     * @return 服务端配置
     */
    private static ServerConfig serverConfig() {
        return serverConfig(0);
    }

    /**
     * 构造绑定 {@code 127.0.0.1} 指定端口的服务端配置。
     *
     * @param port 监听端口；{@code 0} 表示随机
     * @return 服务端配置
     */
    private static ServerConfig serverConfig(int port) {
        ServerConfig config = new ServerConfig();
        config.setHost("127.0.0.1");
        config.setPort(port);
        return config;
    }

    /**
     * 构造仅含地址的客户端配置（心跳与自动重连均关闭）。
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
     * 构造开启自动重连的客户端配置；抖动置零以便确定性断言重试时机。
     *
     * @param uri 目标地址
     * @param maxRetries 连续失败重试上限
     * @return 客户端配置
     */
    private static ClientConfig reconnectConfig(String uri, int maxRetries) {
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
     * 启动服务端并返回已启动实例。
     *
     * @param config 服务端配置
     * @return 已启动的服务端
     */
    private static WebSocketServer startServer(ServerConfig config) {
        return startServer(config, new RecordingServer());
    }

    /**
     * 以指定监听器启动服务端并返回已启动实例。
     *
     * @param config 服务端配置
     * @param listener 服务端回调
     * @return 已启动的服务端
     */
    private static WebSocketServer startServer(ServerConfig config, ServerListener listener) {
        WebSocketServer server = WebSocketKit.createServer(config, listener);
        server.start();
        return server;
    }

    /**
     * 由端口构造回环地址。
     *
     * @param port 端口
     * @return 目标地址
     */
    private static String uri(int port) {
        return "ws://127.0.0.1:" + port + "/";
    }

    /**
     * 构造客户端实例。
     *
     * @param config 客户端配置
     * @param listener 客户端回调
     * @return 客户端实例
     */
    private static WebSocketClient newClient(ClientConfig config, ClientListener listener) {
        return WebSocketKit.createClient(config, listener);
    }

    /**
     * 轮询等待条件成立，超时即失败。
     *
     * @param condition 条件
     * @param timeoutMillis 超时（毫秒）
     * @param message 失败消息
     * @throws InterruptedException 等待被中断
     */
    private static void awaitCondition(BooleanSupplier condition, long timeoutMillis, String message)
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
     * 在给定时长内持续保持条件成立；一旦中途不成立即失败。
     *
     * @param condition 条件
     * @param durationMillis 观察时长（毫秒）
     * @param message 失败消息
     * @throws InterruptedException 等待被中断
     */
    private static void assertStays(BooleanSupplier condition, long durationMillis, String message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + durationMillis;
        while (System.currentTimeMillis() < deadline) {
            assertTrue(message, condition.getAsBoolean());
            Thread.sleep(50L);
        }
    }

    /**
     * 客户端回调记录器：等待建连 / 关闭 / 重连耗尽事件，并收集收到的消息与重连次数。
     */
    private static final class RecordingClient implements ClientListener {

        final CountDownLatch openLatch = new CountDownLatch(1);

        final CountDownLatch closeLatch = new CountDownLatch(1);

        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

        final AtomicInteger openCount = new AtomicInteger();

        final AtomicInteger closeCount = new AtomicInteger();

        final AtomicInteger reconnectCount = new AtomicInteger();

        final AtomicInteger exhaustedCount = new AtomicInteger();

        volatile int lastExhaustedAttempts;

        volatile int lastCloseCode = Integer.MIN_VALUE;

        volatile boolean lastCloseRemote;

        @Override
        public void onOpen() {
            openCount.incrementAndGet();
            openLatch.countDown();
        }

        @Override
        public void onMessage(Message message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            lastCloseCode = code;
            lastCloseRemote = remote;
            closeCount.incrementAndGet();
            closeLatch.countDown();
        }

        @Override
        public void onError(Throwable error) {
        }

        @Override
        public void onReconnect(int attempt, long delayMillis) {
            reconnectCount.incrementAndGet();
        }

        @Override
        public void onReconnectExhausted(int attempts, Throwable lastError) {
            lastExhaustedAttempts = attempts;
            exhaustedCount.incrementAndGet();
        }
    }

    /**
     * 服务端回调记录器：等待会话登记并收集收到的消息。
     */
    private static final class RecordingServer implements ServerListener {

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
     * 回声服务端回调：把收到的消息按原类型回发，验证双向收发。
     */
    private static final class EchoServerListener implements ServerListener {

        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

        @Override
        public void onOpen(WebSocketSession session) {
        }

        @Override
        public void onMessage(WebSocketSession session, Message message) {
            messages.add(message);
            if (message.isText()) {
                session.send(message.asText());
            } else {
                session.send(message.asBytes());
            }
        }

        @Override
        public void onClose(WebSocketSession session, int code, String reason, boolean remote) {
        }
    }
}
