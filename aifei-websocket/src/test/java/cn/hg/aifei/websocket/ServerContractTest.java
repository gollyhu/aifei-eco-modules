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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;

/**
 * 服务端接口的抽象契约测试基类：{@link WebSocketServer} 与 {@link ServerListener} 的公共行为只在此断言一次，
 * 各后端实现继承运行（对应设计 §4.3 / §4.4 / §6.6）。
 *
 * <p>会话登记 / 广播 / 空闲回收等用例需要真实客户端连接，故子类同时提供两端工厂。</p>
 */
public abstract class ServerContractTest {

    /**
     * 事件等待上限（毫秒）。
     */
    protected static final long TIMEOUT_MILLIS = 5000L;

    /**
     * 构造被测服务端的实例。
     *
     * @param config 服务端配置
     * @param listener 服务端回调
     * @return 服务端实例
     */
    protected abstract WebSocketServer newServer(ServerConfig config, ServerListener listener);

    /**
     * 构造供建立会话使用的客户端实例。
     *
     * @param config 客户端配置
     * @param listener 客户端回调
     * @return 客户端实例
     */
    protected abstract WebSocketClient newClient(ClientConfig config, ClientListener listener);

    /**
     * 返回被测后端的类型标识。
     *
     * @return 后端类型，如 {@code "jdk"}
     */
    protected abstract String backendType();

    /**
     * {@code start()} / {@code stop()} 的幂等语义与 {@code isStarted()} / {@code port()} 取值。
     */
    @Test
    public void shouldStartAndStopIdempotently() {
        ServerRecorder listener = new ServerRecorder();
        WebSocketServer server = newServer(loopbackServerConfig(), listener);
        try {
            assertFalse(server.isStarted());
            assertEquals(0, server.port());

            server.stop();
            assertFalse("未启动时 stop 应为无操作", server.isStarted());
            assertEquals(0, listener.stopCount.get());

            server.start();
            assertTrue(server.isStarted());
            assertTrue("已启动时 port() 应为实际端口", server.port() > 0);
            assertEquals(1, listener.startCount.get());

            assertThrows(IllegalStateException.class, server::start);

            server.stop();
            assertFalse(server.isStarted());
            assertEquals(0, server.port());
            assertEquals(1, listener.stopCount.get());

            server.stop();
            assertEquals("重复 stop 不应重复触发 onStop", 1, listener.stopCount.get());
        } finally {
            server.stop();
        }
    }

    /**
     * {@code broadcast*} 的参数为 {@code null} 抛 {@link IllegalArgumentException}。
     */
    @Test
    public void shouldRejectNullBroadcastArguments() {
        WebSocketServer server = newServer(loopbackServerConfig(), new ServerRecorder());
        try {
            assertThrows(IllegalArgumentException.class, () -> server.broadcast((String) null));
            assertThrows(IllegalArgumentException.class, () -> server.broadcast((byte[]) null));
            assertThrows(IllegalArgumentException.class, () -> server.broadcast((Object) null));
        } finally {
            server.stop();
        }
    }

    /**
     * {@code sessions()} 返回不可变快照：即使无会话也不可修改。
     */
    @Test
    public void shouldExposeUnmodifiableSessions() {
        WebSocketServer server = newServer(loopbackServerConfig(), new ServerRecorder());
        try {
            server.start();
            Collection<WebSocketSession> snapshot = server.sessions();
            assertNotNull(snapshot);
            assertTrue(snapshot.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
        } finally {
            server.stop();
        }
    }

    /**
     * 会话登记后可读标识 / 状态 / 远端地址，注销后从登记表移除；已取得的快照不受后续变化影响。
     */
    @Test
    public void shouldRegisterAndUnregisterSession() throws Exception {
        WebSocketServer server = newServer(loopbackServerConfig(), new ServerRecorder());
        ClientProbe probe = new ClientProbe();
        WebSocketClient client = null;
        try {
            server.start();
            client = newClient(clientConfig(uriOf(server)), probe);
            client.connect();
            assertTrue(probe.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitSessionCount(server, 1);

            Collection<WebSocketSession> snapshot = server.sessions();
            assertEquals(1, snapshot.size());
            WebSocketSession session = snapshot.iterator().next();
            assertNotNull(session.id());
            assertSame(ConnectionState.OPEN, session.state());
            assertTrue(session.isOpen());
            assertNotNull(session.remoteAddress());

            client.close();
            awaitSessionCount(server, 0);

            assertEquals("快照是当时状态的副本，不受后续注销影响", 1, snapshot.size());
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 广播送达已登记会话；对已关闭会话静默跳过，无会话时为无操作。
     */
    @Test
    public void shouldBroadcastToOpenSessionAndSkipClosed() throws Exception {
        WebSocketServer server = newServer(loopbackServerConfig(), new ServerRecorder());
        ClientProbe probe = new ClientProbe();
        WebSocketClient client = null;
        try {
            server.start();
            client = newClient(clientConfig(uriOf(server)), probe);
            client.connect();
            assertTrue(probe.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitSessionCount(server, 1);

            server.broadcast("text-notice");
            Message text = probe.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(text);
            assertTrue(text.isText());
            assertEquals("text-notice", text.asText());

            server.broadcast(new byte[] {1, 2, 3});
            Message binary = probe.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(binary);
            assertFalse(binary.isText());
            assertArrayEquals(new byte[] {1, 2, 3}, binary.asBytes());

            client.close();
            awaitSessionCount(server, 0);

            // 无会话时广播为无操作，不得抛异常
            server.broadcast("nobody");
            server.broadcast(new byte[] {9});
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 未配置编解码器时 {@code broadcast(Object)} 抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectObjectBroadcastWithoutCodec() {
        WebSocketServer server = newServer(loopbackServerConfig(), new ServerRecorder());
        try {
            server.start();
            assertThrows(IllegalStateException.class, () -> server.broadcast(new Object()));
        } finally {
            server.stop();
        }
    }

    /**
     * 配置编解码器后 {@code broadcast(Object)} 编码后送达会话。
     */
    @Test
    public void shouldEncodeObjectBroadcastWithCodec() throws Exception {
        ServerConfig config = loopbackServerConfig();
        config.setCodec(new TextCodec());
        WebSocketServer server = newServer(config, new ServerRecorder());
        ClientProbe probe = new ClientProbe();
        WebSocketClient client = null;
        try {
            server.start();
            client = newClient(clientConfig(uriOf(server)), probe);
            client.connect();
            assertTrue(probe.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitSessionCount(server, 1);

            server.broadcast("object-notice");
            Message message = probe.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(message);
            assertEquals("object-notice", message.asText());
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 空闲回收阈值为 {@code 0} 时禁用回收：静默会话在显著长于扫描周期的时间内仍保持登记。
     */
    @Test
    public void shouldNotReclaimIdleSessionWhenDisabled() throws Exception {
        ServerConfig config = loopbackServerConfig();
        config.setSessionIdleTimeoutSeconds(0);
        WebSocketServer server = newServer(config, new ServerRecorder());
        ClientProbe probe = new ClientProbe();
        WebSocketClient client = null;
        try {
            server.start();
            client = newClient(clientConfig(uriOf(server)), probe);
            client.connect();
            assertTrue(probe.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitSessionCount(server, 1);

            assertStays(() -> server.sessions().size() == 1, 2000L, "阈值 0 时不得回收会话");
        } finally {
            if (client != null) {
                client.close();
            }
            server.stop();
        }
    }

    /**
     * 空闲回收阈值生效时，长时间无入站帧的会话被回收：登记表清空且客户端收到正常关闭码 {@code 1000}。
     */
    @Test
    public void shouldReclaimIdleSilentSession() throws Exception {
        ServerConfig config = loopbackServerConfig();
        config.setSessionIdleTimeoutSeconds(1);
        WebSocketServer server = newServer(config, new ServerRecorder());
        ClientProbe probe = new ClientProbe();
        WebSocketClient client = null;
        try {
            server.start();
            client = newClient(clientConfig(uriOf(server)), probe);
            client.connect();
            assertTrue(probe.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            awaitSessionCount(server, 1);

            awaitCondition(() -> server.sessions().isEmpty(), 6000L, "空闲静默会话应在阈值后被回收");
            assertTrue("被回收会话的对端应收到关闭事件", probe.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(1000, probe.lastCloseCode);
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
     * 构造绑定 {@code 127.0.0.1} 随机端口的服务端配置。
     *
     * @return 服务端配置
     */
    protected static ServerConfig loopbackServerConfig() {
        ServerConfig config = new ServerConfig();
        config.setHost("127.0.0.1");
        config.setPort(0);
        return config;
    }

    /**
     * 由服务端实际端口构造回环地址。
     *
     * @param server 已启动的服务端
     * @return 目标地址
     */
    protected static String uriOf(WebSocketServer server) {
        return "ws://127.0.0.1:" + server.port() + "/";
    }

    /**
     * 轮询等待服务端登记的会话数达到期望值。
     *
     * @param server 服务端
     * @param expected 期望会话数
     * @throws InterruptedException 等待被中断
     */
    protected static void awaitSessionCount(WebSocketServer server, int expected) throws InterruptedException {
        awaitCondition(() -> server.sessions().size() == expected, TIMEOUT_MILLIS,
                "会话数未在超时内达到期望值：" + expected);
    }

    /**
     * 轮询等待条件成立，超时即失败。
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
     * 在给定时长内持续保持条件成立；一旦中途不成立即失败。
     *
     * @param condition 条件
     * @param durationMillis 观察时长（毫秒）
     * @param message 失败消息
     * @throws InterruptedException 等待被中断
     */
    protected static void assertStays(BooleanSupplier condition, long durationMillis, String message)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + durationMillis;
        while (System.currentTimeMillis() < deadline) {
            assertTrue(message, condition.getAsBoolean());
            Thread.sleep(50L);
        }
    }

    /**
     * 服务端回调记录器：记录启停次数，并收集会话事件。
     */
    protected static final class ServerRecorder implements ServerListener {

        final AtomicInteger startCount = new AtomicInteger();

        final AtomicInteger stopCount = new AtomicInteger();

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

        @Override
        public void onStart() {
            startCount.incrementAndGet();
        }

        @Override
        public void onStop() {
            stopCount.incrementAndGet();
        }
    }

    /**
     * 客户端探针：等待建连 / 关闭事件并收集收到的消息。
     */
    protected static final class ClientProbe implements ClientListener {

        final CountDownLatch openLatch = new CountDownLatch(1);

        final CountDownLatch closeLatch = new CountDownLatch(1);

        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

        volatile int lastCloseCode = Integer.MIN_VALUE;

        @Override
        public void onOpen() {
            openLatch.countDown();
        }

        @Override
        public void onMessage(Message message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            lastCloseCode = code;
            closeLatch.countDown();
        }

        @Override
        public void onError(Throwable error) {
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
}
