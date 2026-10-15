/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import cn.hg.aifei.websocket.ConnectionState;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketConnectionException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单条连接的传输体：读线程收帧、写侧发帧、心跳调度与探活、关闭握手与关闭码映射都在此实现一次。
 *
 * <p>握手由 {@link Rfc6455Handshake} 完成、帧编解码由 {@link Rfc6455FrameCodec} 完成、Socket 创建与关闭由
 * {@link JdkSocketSupport} 完成，本类只负责把它们编排成一条连接的生命周期：</p>
 *
 * <ul>
 * <li>客户端角色：{@link #connectAsync()} 异步完成建连与握手，成功后回调 {@code onOpen} 并转入读循环；</li>
 * <li>服务端角色：在已完成握手的 {@link Socket} 上构造，{@link #beginReading()} 启动读循环与心跳，
 *     建连成功的 {@code onOpen} 由服务端骨架在登记会话时触发，本类不重复触发；</li>
 * <li>收到 ping 自动回 pong（载荷原样回带），ping / pong 不上浮为 {@link Message}、不产生用户回调，
 *     但 pong 会经 {@link Callback#onPong()} 上报，供服务端把 pong 计入会话活跃；</li>
 * <li>分片（含控制帧穿插）由本类聚合后一次性上浮，聚合上限取 {@link Rfc6455FrameCodec#MAX_AGGREGATED_BYTES}。</li>
 * </ul>
 *
 * <p>本类属内部实现，不是公共 API。线程安全：收发、关闭可从任意线程调用，写侧串行化；读循环由本类自有线程
 * 驱动，回调在该读线程上执行。</p>
 */
public final class JdkWebSocketConnection {

    /**
     * 连接事件的回灌出口：由客户端骨架或服务端会话实现，把传输事件翻译为公共回调。
     */
    public interface Callback {

        /**
         * 握手成功、连接可收发时触发一次（仅客户端角色触发）。
         */
        void onOpen();

        /**
         * 收到一条完整消息（分片已聚合）时触发。
         *
         * @param message 收到的消息
         */
        void onMessage(Message message);

        /**
         * 收到 pong 控制帧时触发；用于把 pong 计入服务端会话活跃。
         */
        void onPong();

        /**
         * 连接终结时触发一次。
         *
         * @param code 关闭码；无可用关闭帧时为 {@code -1}
         * @param reason 关闭原因；可为 {@code null}
         * @param remote 是否非本端主动发起
         */
        void onClose(int code, String reason, boolean remote);

        /**
         * 运行态故障时触发。
         *
         * @param error 已包装为 {@link WebSocketConnectionException} 的故障
         */
        void onError(Throwable error);
    }

    /**
     * RFC6455 正常关闭码：用户主动关闭与关闭帧回显缺省时使用。
     */
    public static final int NORMAL_CLOSURE = 1000;

    /**
     * 无可用关闭帧时的关闭码，与公共契约一致。
     */
    public static final int NO_CLOSE_CODE = -1;

    /**
     * 心跳检测任务的执行周期（毫秒）；心跳周期与探活判定都按该节拍检查。
     */
    private static final long HEARTBEAT_TICK_MILLIS = 200L;

    /**
     * 流缓冲区大小（字节）。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * {@code ws} 缺省端口。
     */
    private static final int DEFAULT_WS_PORT = 80;

    /**
     * {@code wss} 缺省端口。
     */
    private static final int DEFAULT_WSS_PORT = 443;

    /**
     * 心跳线程工厂：线程为守护线程，随 JVM 退出而终止。
     */
    private static final ThreadFactory HEARTBEAT_THREAD_FACTORY = new ThreadFactory() {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "aifei-websocket-heartbeat-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    };

    /**
     * 全部连接共用的心跳调度器；未开启心跳的连接不产生任务，也就不占用本线程。
     */
    private static final ScheduledExecutorService HEARTBEAT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(HEARTBEAT_THREAD_FACTORY);

    /**
     * 心跳 ping 帧的异步写出执行器：心跳调度线程只负责判定与提交写任务，实际的同步 {@code flush} 交由本执行器，
     * 避免慢连接的阻塞式写出拖垮 {@link #HEARTBEAT_EXECUTOR} 单线程、进而令所有连接的心跳被连带阻塞。
     */
    private static final ExecutorService HEARTBEAT_WRITE_EXECUTOR =
            Executors.newSingleThreadExecutor(HEARTBEAT_THREAD_FACTORY);

    /**
     * 读线程与建连线程的序号，仅用于线程命名。
     */
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    private final Object lock = new Object();

    private final Object writeLock = new Object();

    private final Callback callback;

    private final boolean clientRole;

    private final int heartbeatSeconds;

    private final String uri;

    private final Map<String, String> headers;

    private final int connectTimeoutMillis;

    private Socket socket;

    private InputStream in;

    private OutputStream out;

    private volatile ConnectionState state;

    private volatile boolean closeReported;

    private volatile boolean aborted;

    private volatile boolean locallyClosed;

    private volatile long lastPingMillis;

    private volatile long lastPongMillis;

    private volatile ScheduledFuture<?> heartbeatTask;

    /**
     * 未完成的分片累加缓冲；受读线程独占。
     */
    private ByteArrayOutputStream fragments;

    /**
     * 当前分片序列的首帧 opcode；受读线程独占。
     */
    private int fragmentOpcode;

    /**
     * 以客户端角色构造一条连接：稍后由 {@link #connectAsync()} 异步完成建连与握手。
     *
     * @param uri 目标地址，形如 {@code ws://host:port/path} 或 {@code wss://host:port/path}
     * @param headers 握手附加请求头；可为 {@code null}，内部做副本
     * @param connectTimeoutMillis 连接超时（毫秒）
     * @param heartbeatSeconds 心跳周期（秒）；{@code 0} 表示禁用
     * @param callback 事件回灌出口
     * @throws IllegalArgumentException {@code uri} 或 {@code callback} 为 {@code null}
     */
    public JdkWebSocketConnection(String uri, Map<String, String> headers, int connectTimeoutMillis,
            int heartbeatSeconds, Callback callback) {
        if (uri == null) {
            throw new IllegalArgumentException("uri can not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback can not be null");
        }
        this.uri = uri;
        this.headers = new LinkedHashMap<String, String>();
        if (headers != null) {
            this.headers.putAll(headers);
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.heartbeatSeconds = heartbeatSeconds;
        this.callback = callback;
        this.clientRole = true;
        this.state = ConnectionState.CONNECTING;
    }

    /**
     * 以服务端角色构造一条连接：握手已由调用方完成，本对象直接从读循环开始工作。
     *
     * @param socket 已完成握手的套接字
     * @param heartbeatSeconds 心跳周期（秒）；{@code 0} 表示禁用
     * @param callback 事件回灌出口
     * @throws IllegalArgumentException {@code socket} 或 {@code callback} 为 {@code null}
     * @throws WebSocketConnectionException 取套接字流失败
     */
    public JdkWebSocketConnection(Socket socket, int heartbeatSeconds, Callback callback) {
        if (socket == null) {
            throw new IllegalArgumentException("socket can not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback can not be null");
        }
        this.socket = socket;
        this.heartbeatSeconds = heartbeatSeconds;
        this.callback = callback;
        this.clientRole = false;
        this.state = ConnectionState.OPEN;
        this.uri = null;
        this.headers = new LinkedHashMap<String, String>();
        this.connectTimeoutMillis = 0;
        try {
            this.in = new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE);
            this.out = new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE);
        } catch (IOException e) {
            throw new WebSocketConnectionException("failed to open connection streams", e);
        }
    }

    /**
     * 客户端角色：异步完成建连与握手，成功后回调 {@code onOpen} 并转入读循环。
     *
     * <p>失败一律收敛为「{@code onError} → {@code onClose}（关闭码 {@code -1}）」，且只上报一次。</p>
     */
    public void connectAsync() {
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                connectAndRead();
            }
        }, "aifei-websocket-client-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 服务端角色：启动心跳与读循环；不触发 {@code onOpen}（由服务端骨架在登记会话时触发）。
     */
    public void beginReading() {
        startHeartbeat();
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                readLoop();
            }
        }, "aifei-websocket-reader-" + THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 发送一条文本消息。
     *
     * @param text 文本内容
     * @throws IllegalArgumentException {@code text} 为 {@code null}
     * @throws WebSocketConnectionException 连接非 {@code OPEN}，或写出失败
     */
    public void sendText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        writeDataFrame(Rfc6455FrameCodec.OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 发送一条二进制消息。
     *
     * @param bytes 二进制内容
     * @throws IllegalArgumentException {@code bytes} 为 {@code null}
     * @throws WebSocketConnectionException 连接非 {@code OPEN}，或写出失败
     */
    public void sendBinary(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        writeDataFrame(Rfc6455FrameCodec.OPCODE_BINARY, bytes);
    }

    /**
     * 关闭本条连接：发出关闭帧、上报一次 {@code onClose}（{@code remote = false}）并释放套接字。
     *
     * <p>尚在建连时（{@code CONNECTING}）只中止本次建连：不上报 {@code onClose}，由调用方骨架负责收敛。
     * 已关闭时为无操作（幂等）。</p>
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     */
    public void close(int code, String reason) {
        boolean wasConnecting;
        synchronized (lock) {
            if (state == ConnectionState.CLOSED) {
                return;
            }
            wasConnecting = state == ConnectionState.CONNECTING;
            if (wasConnecting) {
                aborted = true;
                state = ConnectionState.CLOSED;
            } else {
                locallyClosed = true;
                state = ConnectionState.CLOSING;
            }
        }
        if (wasConnecting) {
            closeSocket();
            return;
        }
        try {
            writeCloseFrame(code, reason);
        } catch (IOException ignored) {
            // 关闭帧尽力发出，发出失败仍按本端关闭收敛
        }
        reportClose(code, reason, false);
        closeSocket();
        synchronized (lock) {
            state = ConnectionState.CLOSED;
        }
        cancelHeartbeat();
    }

    /**
     * 返回本条连接的状态。
     *
     * @return 连接状态
     */
    public ConnectionState state() {
        return state;
    }

    /**
     * 返回远端地址；尚未建连或不可用时返回 {@code null}。
     *
     * @return 远端地址；不可用时 {@code null}
     */
    public String remoteAddress() {
        Socket current;
        synchronized (lock) {
            current = socket;
        }
        if (current == null) {
            return null;
        }
        return JdkSocketSupport.formatAddress(current.getRemoteSocketAddress());
    }

    // ─────────── 内部实现：生命周期 ───────────

    /**
     * 客户端角色的建连线程主体：建连 → 握手 → {@code onOpen} → 读循环；任一环节失败按同一条路径收敛。
     */
    private void connectAndRead() {
        try {
            URI target = URI.create(uri);
            boolean secure = "wss".equalsIgnoreCase(target.getScheme());
            String host = target.getHost();
            if (host == null) {
                throw new IOException("websocket uri must carry a host: " + uri);
            }
            int defaultPort = secure ? DEFAULT_WSS_PORT : DEFAULT_WS_PORT;
            int port = target.getPort() == -1 ? defaultPort : target.getPort();
            String rawPath = target.getRawPath();
            String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
            String query = target.getRawQuery();
            String requestTarget = query == null ? path : path + "?" + query;
            String hostHeader = port == defaultPort ? host : host + ":" + port;

            Socket connected = JdkSocketSupport.connect(host, port, connectTimeoutMillis, secure);
            synchronized (lock) {
                if (aborted) {
                    JdkSocketSupport.closeQuietly(connected);
                    return;
                }
                socket = connected;
                in = new BufferedInputStream(connected.getInputStream(), BUFFER_SIZE);
                out = new BufferedOutputStream(connected.getOutputStream(), BUFFER_SIZE);
            }
            String key = Rfc6455Handshake.newKey();
            Rfc6455Handshake.writeRequest(out, hostHeader, requestTarget, key, headers);
            out.flush();
            Rfc6455Handshake.Response response = Rfc6455Handshake.readResponse(in);
            Rfc6455Handshake.verifyResponse(response, Rfc6455Handshake.acceptFor(key));
            synchronized (lock) {
                if (aborted) {
                    closeSocket();
                    return;
                }
                state = ConnectionState.OPEN;
            }
            startHeartbeat();
            callback.onOpen();
            readLoop();
        } catch (Throwable error) {
            if (!aborted) {
                callback.onError(toFailure(error));
                reportClose(NO_CLOSE_CODE, null, true);
            }
            closeSocket();
        }
    }

    /**
     * 读循环：解码入站帧并按帧类型分派；返回即表示本条连接终结（已上报关闭）。
     */
    private void readLoop() {
        try {
            while (true) {
                Rfc6455FrameCodec.Frame frame = Rfc6455FrameCodec.decode(in, !clientRole);
                if (frame == null) {
                    handleDisconnect();
                    return;
                }
                if (!handleFrame(frame)) {
                    return;
                }
            }
        } catch (Rfc6455FrameCodec.ProtocolException e) {
            if (!isSuppressed()) {
                handleFailure(e.closeCode(), e);
            }
        } catch (IOException e) {
            if (!isSuppressed()) {
                handleFailure(NO_CLOSE_CODE, e);
            }
        } catch (RuntimeException e) {
            if (!isSuppressed()) {
                handleFailure(NO_CLOSE_CODE, e);
            }
        } finally {
            closeSocket();
            cancelHeartbeat();
            synchronized (lock) {
                state = ConnectionState.CLOSED;
            }
        }
    }

    /**
     * 处理一个入站帧。
     *
     * @param frame 已解码的帧
     * @return 继续读循环时 {@code true}；收到关闭帧时为 {@code false}
     * @throws IOException 自动回 pong / 回显关闭帧时写出失败，或分片聚合发现协议错误
     */
    private boolean handleFrame(Rfc6455FrameCodec.Frame frame) throws IOException {
        int opcode = frame.opcode();
        if (opcode == Rfc6455FrameCodec.OPCODE_PING) {
            sendControlFrame(Rfc6455FrameCodec.OPCODE_PONG, frame.payload());
            return true;
        }
        if (opcode == Rfc6455FrameCodec.OPCODE_PONG) {
            lastPongMillis = System.currentTimeMillis();
            callback.onPong();
            return true;
        }
        if (opcode == Rfc6455FrameCodec.OPCODE_CLOSE) {
            int code = Rfc6455FrameCodec.closeCode(frame.payload());
            String reason = Rfc6455FrameCodec.closeReason(frame.payload());
            byte[] echo = code == Rfc6455FrameCodec.NO_CLOSE_CODE
                    ? new byte[0]
                    : Rfc6455FrameCodec.closePayload(code, null);
            sendControlFrame(Rfc6455FrameCodec.OPCODE_CLOSE, echo);
            reportClose(code, reason, true);
            return false;
        }
        accumulate(frame);
        return true;
    }

    /**
     * 分片聚合：数据帧与延续帧按序拼接，控制帧由 {@link #handleFrame(Rfc6455FrameCodec.Frame)} 先行处理，
     * 因此自然支持控制帧穿插在分片序列中间。
     *
     * @param frame 数据帧或延续帧
     * @throws Rfc6455FrameCodec.ProtocolException 分片序列非法，或聚合后超过上限
     */
    private void accumulate(Rfc6455FrameCodec.Frame frame) throws Rfc6455FrameCodec.ProtocolException {
        int opcode = frame.opcode();
        if (opcode == Rfc6455FrameCodec.OPCODE_CONTINUATION) {
            if (fragments == null) {
                throw new Rfc6455FrameCodec.ProtocolException(Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR,
                        "unexpected continuation frame");
            }
            appendFragment(frame.payload());
            if (frame.fin()) {
                byte[] data = fragments.toByteArray();
                fragments = null;
                deliver(fragmentOpcode, data);
            }
            return;
        }
        if (fragments != null) {
            throw new Rfc6455FrameCodec.ProtocolException(Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR,
                    "expected continuation frame");
        }
        if (frame.fin()) {
            deliver(opcode, frame.payload());
            return;
        }
        fragmentOpcode = opcode;
        fragments = new ByteArrayOutputStream();
        appendFragment(frame.payload());
    }

    /**
     * 追加一段分片载荷；超出聚合上限即以关闭码 {@code 1009} 报错。
     *
     * @param payload 本段载荷
     * @throws Rfc6455FrameCodec.ProtocolException 聚合后超过 16 MiB 上限
     */
    private void appendFragment(byte[] payload) throws Rfc6455FrameCodec.ProtocolException {
        if ((long) fragments.size() + (long) payload.length > (long) Rfc6455FrameCodec.MAX_AGGREGATED_BYTES) {
            fragments = null;
            throw new Rfc6455FrameCodec.ProtocolException(Rfc6455FrameCodec.CLOSE_MESSAGE_TOO_BIG,
                    "aggregated message exceeds " + Rfc6455FrameCodec.MAX_AGGREGATED_BYTES + " bytes");
        }
        fragments.write(payload, 0, payload.length);
    }

    /**
     * 上浮一条完整消息：文本按 UTF-8 严格解码（非法即 {@code 1007}），二进制原样上浮。
     *
     * @param opcode 首帧 opcode
     * @param data 聚合后的载荷
     * @throws Rfc6455FrameCodec.ProtocolException 文本载荷不是合法 UTF-8
     */
    private void deliver(int opcode, byte[] data) throws Rfc6455FrameCodec.ProtocolException {
        if (opcode == Rfc6455FrameCodec.OPCODE_TEXT) {
            callback.onMessage(Message.text(Rfc6455FrameCodec.decodeUtf8(data)));
            return;
        }
        callback.onMessage(Message.binary(data));
    }

    /**
     * 对端在无关闭帧的情况下断开（读到流尾）：按「无关闭帧」上报一次 {@code onClose}。
     */
    private void handleDisconnect() {
        if (isSuppressed()) {
            return;
        }
        reportClose(NO_CLOSE_CODE, null, true);
    }

    /**
     * 运行态故障收敛：先转投 {@code onError}，再按映射出的关闭码发关闭帧并上报 {@code onClose}。
     *
     * @param closeCode 映射出的关闭码；无可用关闭码时为 {@link #NO_CLOSE_CODE}
     * @param error 原始故障
     */
    private void handleFailure(int closeCode, Throwable error) {
        callback.onError(toFailure(error));
        if (closeCode != NO_CLOSE_CODE) {
            try {
                writeCloseFrame(closeCode, null);
            } catch (IOException ignored) {
                // 关闭帧尽力发出，发出失败仍按故障收敛
            }
        }
        reportClose(closeCode, null, true);
    }

    /**
     * 判断是否应抑制故障上报：本端已主动关闭或已中止建连时，关闭事件已由本端收敛，不再重复上报。
     *
     * @return 应抑制时 {@code true}
     */
    private boolean isSuppressed() {
        return aborted || locallyClosed || closeReported;
    }

    /**
     * 上报一次关闭；同一条连接只上报一次，保证 {@code onClose} 恰好一次。
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     * @param remote 是否非本端主动发起
     */
    private void reportClose(int code, String reason, boolean remote) {
        synchronized (lock) {
            if (closeReported) {
                return;
            }
            closeReported = true;
        }
        callback.onClose(code, reason, remote);
    }

    // ─────────── 内部实现：写侧 ───────────

    /**
     * 写出一个数据帧；非 {@code OPEN} 时抛运行态故障（消息不排队、直接丢弃）。
     *
     * @param opcode 帧 opcode
     * @param payload 载荷
     * @throws WebSocketConnectionException 连接非 {@code OPEN}，或写出失败
     */
    private void writeDataFrame(int opcode, byte[] payload) {
        synchronized (writeLock) {
            ConnectionState current = state;
            if (current != ConnectionState.OPEN) {
                throw new WebSocketConnectionException("send is only allowed when connection is OPEN but was " + current);
            }
            try {
                out.write(Rfc6455FrameCodec.encode(opcode, true, payload, clientRole));
                out.flush();
            } catch (IOException e) {
                throw new WebSocketConnectionException("failed to send frame", e);
            }
        }
    }

    /**
     * 写出一个控制帧（pong / 关闭回显 / 心跳 ping）；连接已不可写时静默跳过。
     *
     * @param opcode 控制帧 opcode
     * @param payload 载荷
     */
    private void sendControlFrame(int opcode, byte[] payload) {
        synchronized (writeLock) {
            if (state != ConnectionState.OPEN) {
                return;
            }
            try {
                out.write(Rfc6455FrameCodec.encode(opcode, true, payload, clientRole));
                out.flush();
            } catch (IOException ignored) {
                // 控制帧尽力发出，写出失败交由读循环或心跳探活收敛
            }
        }
    }

    /**
     * 写出关闭帧。
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     * @throws IOException 写出失败
     */
    private void writeCloseFrame(int code, String reason) throws IOException {
        synchronized (writeLock) {
            out.write(Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_CLOSE, true,
                    Rfc6455FrameCodec.closePayload(code, reason), clientRole));
            out.flush();
        }
    }

    // ─────────── 内部实现：心跳与释放 ───────────

    /**
     * 启动心跳：按 {@code heartbeatSeconds} 周期发 ping，并在 {@code 1.5 × heartbeatSeconds} 内未收到 pong 时
     * 判定连接丢失并关闭。{@code heartbeatSeconds <= 0} 时不启用。
     */
    private void startHeartbeat() {
        if (heartbeatSeconds <= 0) {
            return;
        }
        final long periodMillis = heartbeatSeconds * 1000L;
        final long timeoutMillis = (long) (periodMillis * 1.5);
        lastPingMillis = 0L;
        lastPongMillis = System.currentTimeMillis();
        heartbeatTask = HEARTBEAT_EXECUTOR.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                tickHeartbeat(periodMillis, timeoutMillis);
            }
        }, 0L, HEARTBEAT_TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 一次心跳检查：到周期即发 ping；超过探活上限未收到 pong 即判连接丢失并关闭。
     *
     * @param periodMillis ping 周期（毫秒）
     * @param timeoutMillis 探活上限（毫秒）
     */
    private void tickHeartbeat(long periodMillis, long timeoutMillis) {
        synchronized (lock) {
            if (state != ConnectionState.OPEN) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastPingMillis >= periodMillis) {
            lastPingMillis = now;
            HEARTBEAT_WRITE_EXECUTOR.submit(new Runnable() {

                @Override
                public void run() {
                    sendControlFrame(Rfc6455FrameCodec.OPCODE_PING, new byte[0]);
                }
            });
        }
        if (now - lastPongMillis <= timeoutMillis) {
            return;
        }
        synchronized (lock) {
            if (state != ConnectionState.OPEN) {
                return;
            }
            locallyClosed = true;
            state = ConnectionState.CLOSING;
        }
        callback.onError(new WebSocketConnectionException("heartbeat timeout: no pong received"));
        reportClose(NO_CLOSE_CODE, null, true);
        closeSocket();
        synchronized (lock) {
            state = ConnectionState.CLOSED;
        }
        cancelHeartbeat();
    }

    /**
     * 取消心跳任务。
     */
    private void cancelHeartbeat() {
        ScheduledFuture<?> task = heartbeatTask;
        if (task != null) {
            task.cancel(false);
            heartbeatTask = null;
        }
    }

    /**
     * 关闭套接字；失败不上浮。
     */
    private void closeSocket() {
        Socket current;
        synchronized (lock) {
            current = socket;
        }
        JdkSocketSupport.closeQuietly(current);
    }

    /**
     * 把任意故障包装为 {@link WebSocketConnectionException}；已是该类型时原样返回，避免重复包装。
     *
     * @param error 原始故障
     * @return 运行态故障
     */
    private WebSocketConnectionException toFailure(Throwable error) {
        if (error instanceof WebSocketConnectionException) {
            return (WebSocketConnectionException) error;
        }
        return new WebSocketConnectionException("web socket transport failure", error);
    }
}
