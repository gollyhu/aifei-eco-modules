/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.ConnectionState;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketKit;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * 连接传输体的行为测试（设计 §5.3 / §5.4 / §5.5）：分片聚合、控制帧穿插、自动回 pong、心跳探活超时、
 * 协议错误映射与关闭竞态。
 *
 * <p>对端由 {@link FakeEndpoint} 扮演：它是一条裸 {@link Socket}，手工完成握手后直接收发帧，从而可以精确
 * 构造分片序列、非法帧与「永不回 pong」等场景；被测侧一律经公共工厂 {@link WebSocketKit} 创建。</p>
 */
public class JdkWebSocketConnectionTest {

    /**
     * 事件等待上限（毫秒）。
     */
    private static final long TIMEOUT_MILLIS = 5000L;

    /**
     * 分片序列中的控制帧必须被先行处理，且不影响数据帧的聚合结果。
     *
     * @throws Exception 建连或收发失败
     */
    @Test
    public void shouldAggregateFragmentsWithInterleavedControlFrames() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 0), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            endpoint.send(Rfc6455FrameCodec.OPCODE_TEXT, false, bytes("Hel"));
            endpoint.send(Rfc6455FrameCodec.OPCODE_PING, true, bytes("ping-1"));
            endpoint.send(Rfc6455FrameCodec.OPCODE_CONTINUATION, false, bytes("lo "));
            endpoint.send(Rfc6455FrameCodec.OPCODE_CONTINUATION, true, bytes("World"));

            Message message = listener.messages.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull("分片消息应在超时内聚合上浮", message);
            assertTrue(message.isText());
            assertEquals("Hello World", message.asText());

            Rfc6455FrameCodec.Frame pong = endpoint.readFrame();
            assertEquals(Rfc6455FrameCodec.OPCODE_PONG, pong.opcode());
            assertArrayEquals(bytes("ping-1"), pong.payload());

            Thread.sleep(300L);
            assertEquals("分片只应上浮一条完整消息", 1, listener.messageCount.get());
        } finally {
            client.close();
            endpoint.close();
        }
    }

    /**
     * 收到 ping 必须自动回 pong（载荷原样回带），且 ping / pong 不上浮为消息、不产生用户回调。
     *
     * @throws Exception 建连或收发失败
     */
    @Test
    public void shouldAutoReplyPongWithSamePayload() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 0), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            endpoint.send(Rfc6455FrameCodec.OPCODE_PING, true, bytes("heartbeat"));

            Rfc6455FrameCodec.Frame pong = endpoint.readFrame();
            assertEquals(Rfc6455FrameCodec.OPCODE_PONG, pong.opcode());
            assertArrayEquals(bytes("heartbeat"), pong.payload());
            assertEquals(0, listener.messageCount.get());
        } finally {
            client.close();
            endpoint.close();
        }
    }

    /**
     * 心跳探活：在 {@code 1.5 × heartbeatSeconds} 内未收到 pong 即判连接丢失并关闭，关闭码为 {@code -1}。
     *
     * @throws Exception 建连失败
     */
    @Test
    public void shouldCloseConnectionWhenPongIsMissing() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 1), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            long startedMillis = System.currentTimeMillis();

            // 对端保持静默：既不回 pong，也不发任何帧
            assertTrue("心跳超时应在超时内关闭连接", listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            long elapsedMillis = System.currentTimeMillis() - startedMillis;

            assertEquals("无关闭帧时关闭码应为 -1", -1, listener.lastCloseCode);
            assertNotNull("心跳超时应转投 onError", listener.lastError.get());
            assertSame(ConnectionState.CLOSED, client.state());
            assertTrue("关闭时机应在 1.5 倍心跳周期附近，实际 " + elapsedMillis + "ms", elapsedMillis < 3500L);
        } finally {
            client.close();
            endpoint.close();
        }
    }

    /**
     * 收到对端关闭帧：回显关闭码，并以对端关闭码与原因上报 {@code onClose}（{@code remote = true}）。
     *
     * @throws Exception 建连或收发失败
     */
    @Test
    public void shouldEchoAndReportPeerCloseFrame() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 0), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            endpoint.send(Rfc6455FrameCodec.OPCODE_CLOSE, true,
                    Rfc6455FrameCodec.closePayload(4001, "bye"));

            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(4001, listener.lastCloseCode);
            assertEquals("bye", listener.lastCloseReason);
            assertTrue("对端发起的关闭 remote 应为 true", listener.lastCloseRemote);

            Rfc6455FrameCodec.Frame echo = endpoint.readFrame();
            assertEquals(Rfc6455FrameCodec.OPCODE_CLOSE, echo.opcode());
            assertEquals(4001, Rfc6455FrameCodec.closeCode(echo.payload()));
        } finally {
            client.close();
            endpoint.close();
        }
    }

    /**
     * 帧格式非法（未定义 opcode）时以关闭码 {@code 1002} 关闭并转投 {@code onError}。
     *
     * @throws Exception 建连失败
     */
    @Test
    public void shouldCloseWithProtocolErrorOnInvalidFrame() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 0), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            endpoint.sendRaw(new byte[] {(byte) 0x83, 0x00});

            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR, listener.lastCloseCode);
            assertNotNull(listener.lastError.get());
        } finally {
            client.close();
            endpoint.close();
        }
    }

    /**
     * 单帧声明的长度超过 16 MiB 聚合上限时以关闭码 {@code 1009} 关闭。
     *
     * @throws Exception 建连失败
     */
    @Test
    public void shouldCloseWithMessageTooBigOnOversizedFrame() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 0), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            byte[] header = new byte[10];
            header[0] = (byte) 0x82;
            header[1] = (byte) 0x7F;
            long tooBig = (long) Rfc6455FrameCodec.MAX_AGGREGATED_BYTES + 1L;
            for (int i = 0; i < 8; i++) {
                header[2 + i] = (byte) ((tooBig >>> (56 - 8 * i)) & 0xFFL);
            }
            endpoint.sendRaw(header);

            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(Rfc6455FrameCodec.CLOSE_MESSAGE_TOO_BIG, listener.lastCloseCode);
        } finally {
            client.close();
            endpoint.close();
        }
    }

    /**
     * 关闭竞态：本端 {@code close()} 与对端关闭帧并发时，{@code onClose} 恰好一次且收敛到 {@code CLOSED}。
     *
     * @throws Exception 建连失败
     */
    @Test
    public void shouldFireOnCloseExactlyOnceUnderConcurrentClose() throws Exception {
        FakeEndpoint endpoint = new FakeEndpoint();
        RecordingListener listener = new RecordingListener();
        final WebSocketClient client = WebSocketKit.createClient(config(endpoint.uri(), 0), listener);
        try {
            client.connect();
            endpoint.awaitAccepted(TIMEOUT_MILLIS);
            assertTrue(listener.openLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            final CountDownLatch start = new CountDownLatch(1);
            Thread closer = new Thread(new Runnable() {

                @Override
                public void run() {
                    awaitQuietly(start);
                    client.close();
                }
            }, "concurrent-closer");
            closer.start();
            start.countDown();
            endpoint.send(Rfc6455FrameCodec.OPCODE_CLOSE, true, Rfc6455FrameCodec.closePayload(1000, "bye"));
            closer.join(TIMEOUT_MILLIS);

            assertTrue(listener.closeLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            Thread.sleep(500L);
            assertEquals("onClose 必须恰好一次", 1, listener.closeCount.get());
            assertSame(ConnectionState.CLOSED, client.state());
        } finally {
            client.close();
            endpoint.close();
        }
    }

    // ─────────── 测试工具 ───────────

    /**
     * 构造客户端配置。
     *
     * @param uri 目标地址
     * @param heartbeatSeconds 心跳周期（秒）；{@code 0} 表示禁用
     * @return 客户端配置
     */
    private static ClientConfig config(String uri, int heartbeatSeconds) {
        ClientConfig config = new ClientConfig();
        config.setUri(uri);
        config.setHeartbeatSeconds(heartbeatSeconds);
        return config;
    }

    /**
     * 由文本取 UTF-8 字节。
     *
     * @param text 文本
     * @return 字节序列
     */
    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 安静地等待信号；被中断仅复位中断标记，不干扰用例结论。
     *
     * @param latch 信号
     */
    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 裸对端：手工完成握手后直接收发帧，用于构造协议层场景。
     */
    private static final class FakeEndpoint implements Closeable {

        private final ServerSocket serverSocket;

        private final CountDownLatch acceptedLatch = new CountDownLatch(1);

        private volatile Socket socket;

        private volatile InputStream in;

        private volatile OutputStream out;

        private volatile Throwable failure;

        /**
         * 绑定回环随机端口并立即在后台等待一次建连。
         *
         * @throws IOException 绑定失败
         */
        FakeEndpoint() throws IOException {
            serverSocket = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
            Thread acceptor = new Thread(new Runnable() {

                @Override
                public void run() {
                    acceptAndHandshake();
                }
            }, "fake-ws-endpoint");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        /**
         * 返回本对端的地址。
         *
         * @return 目标地址
         */
        String uri() {
            return "ws://127.0.0.1:" + serverSocket.getLocalPort() + "/";
        }

        /**
         * 等待建连与握手完成；握手失败即断言失败。
         *
         * @param timeoutMillis 等待上限（毫秒）
         * @throws InterruptedException 等待被中断
         */
        void awaitAccepted(long timeoutMillis) throws InterruptedException {
            assertTrue("对端应在超时内接受建连", acceptedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS));
            assertNull("对端握手不应失败：" + failure, failure);
        }

        /**
         * 发出一个未经掩码的帧（服务端角色）。
         *
         * @param opcode 帧 opcode
         * @param fin 是否为最后一帧
         * @param payload 载荷
         * @throws IOException 写出失败
         */
        void send(int opcode, boolean fin, byte[] payload) throws IOException {
            out.write(Rfc6455FrameCodec.encode(opcode, fin, payload, false));
            out.flush();
        }

        /**
         * 直接写出原始字节，用于构造非法帧。
         *
         * @param data 原始字节
         * @throws IOException 写出失败
         */
        void sendRaw(byte[] data) throws IOException {
            out.write(data);
            out.flush();
        }

        /**
         * 读取一个客户端发来的帧（客户端帧必带掩码）。
         *
         * @return 解码出的帧
         * @throws IOException 读取失败或帧非法
         */
        Rfc6455FrameCodec.Frame readFrame() throws IOException {
            return Rfc6455FrameCodec.decode(in, true);
        }

        @Override
        public void close() {
            closeQuietly(socket);
            closeQuietly(serverSocket);
        }

        /**
         * 接受一次建连并完成握手。
         */
        private void acceptAndHandshake() {
            try {
                Socket accepted = serverSocket.accept();
                accepted.setTcpNoDelay(true);
                accepted.setSoTimeout((int) TIMEOUT_MILLIS * 2);
                InputStream input = accepted.getInputStream();
                OutputStream output = accepted.getOutputStream();
                Rfc6455Handshake.Request request = Rfc6455Handshake.readRequest(input);
                String key = Rfc6455Handshake.requireKey(request);
                Rfc6455Handshake.writeResponse(output, Rfc6455Handshake.acceptFor(key));
                output.flush();
                this.socket = accepted;
                this.in = input;
                this.out = output;
            } catch (Throwable error) {
                this.failure = error;
            } finally {
                acceptedLatch.countDown();
            }
        }

        /**
         * 安全关闭套接字。
         *
         * @param closeable 待关闭对象
         */
        private static void closeQuietly(Closeable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (IOException ignored) {
                // 关闭失败不影响用例结论
            }
        }
    }

    /**
     * 客户端回调记录器：等待建连 / 关闭事件，并记录关闭码、原因、消息与最近错误。
     */
    private static final class RecordingListener implements ClientListener {

        final CountDownLatch openLatch = new CountDownLatch(1);

        final CountDownLatch closeLatch = new CountDownLatch(1);

        final BlockingQueue<Message> messages = new LinkedBlockingQueue<>();

        final AtomicInteger messageCount = new AtomicInteger();

        final AtomicInteger closeCount = new AtomicInteger();

        final AtomicReference<Throwable> lastError = new AtomicReference<>();

        volatile int lastCloseCode = Integer.MIN_VALUE;

        volatile String lastCloseReason;

        volatile boolean lastCloseRemote;

        @Override
        public void onOpen() {
            openLatch.countDown();
        }

        @Override
        public void onMessage(Message message) {
            messageCount.incrementAndGet();
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            lastCloseCode = code;
            lastCloseReason = reason;
            lastCloseRemote = remote;
            closeCount.incrementAndGet();
            closeLatch.countDown();
        }

        @Override
        public void onError(Throwable error) {
            lastError.set(error);
        }
    }
}
