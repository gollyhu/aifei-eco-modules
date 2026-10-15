/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ClientListener;
import cn.hg.aifei.websocket.ConnectionState;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketClient;
import cn.hg.aifei.websocket.WebSocketConnectionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 跨后端共享的客户端骨架：状态机、发送前置校验、编解码路径、关闭幂等与自动重连编排都在此实现一次。
 *
 * <p>传输后端只实现建连、发送、关闭、远端地址四个钩子：{@link #doConnect()} / {@link #doSendText(String)} /
 * {@link #doSendBinary(byte[])} / {@link #doClose(int, String)} / {@link #doRemoteAddress()}。底层回调经
 * {@link #handleOpen()} / {@link #handleMessage(Message)} / {@link #handleClose(int, String, boolean)} /
 * {@link #handleError(Throwable)} 回灌本骨架，由本骨架驱动状态迁移与监听器回调。状态迁移、退避、校验与
 * 回调保护各自只有一处实现，后端不得复制。</p>
 *
 * <p>线程安全：公共方法可从任意线程调用。状态以 {@code volatile} 发布、迁移在内部锁内完成；自动重连在
 * 本类私有的单线程守护调度器上执行，不经传输回调线程。调度器线程按需创建，若从未触发重连则不产生线程。</p>
 */
public abstract class AbstractWebSocketClient implements WebSocketClient {

    /**
     * RFC6455 正常关闭码。
     */
    private static final int NORMAL_CLOSURE = 1000;

    /**
     * 无关闭帧（连接未建立或异常中断）时使用的状态码，与传输层约定一致。
     */
    private static final int NO_STATUS_CODE = -1;

    /**
     * 重连调度线程工厂：线程为守护线程，随 JVM 退出而终止。
     */
    private static final ThreadFactory RECONNECT_THREAD_FACTORY = new ThreadFactory() {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "aifei-websocket-reconnect-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    };

    private final ClientConfig config;

    private final ClientListener listener;

    /**
     * 保护状态迁移与重连编排相关字段的内部锁。
     */
    private final Object lock = new Object();

    private final ScheduledExecutorService scheduler;

    private volatile ConnectionState state = ConnectionState.CLOSED;

    private volatile Throwable lastError;

    /**
     * 已连续发起的重连次数；受 {@link #lock} 保护。
     */
    private int reconnectAttempt;

    /**
     * 用户是否主动发起过关闭；主动关闭后永不自动重连，直至下次显式 {@link #connect()}。受 {@link #lock} 保护。
     */
    private boolean userClosed;

    /**
     * 待定的重连任务；受 {@link #lock} 保护。
     */
    private ScheduledFuture<?> pendingReconnect;

    /**
     * 保存装配输入；跨字段校验（地址协议、重试延迟上下限）在构建时于此完成。
     *
     * @param config 客户端配置
     * @param listener 客户端监听器
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}，或跨字段校验不通过
     */
    protected AbstractWebSocketClient(ClientConfig config, ClientListener listener) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        WebSocketValidator.requireUriScheme(config.uri());
        WebSocketValidator.requireMaxRetryDelayMillis(config.initialRetryDelayMillis(), config.maxRetryDelayMillis());
        this.config = config;
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(RECONNECT_THREAD_FACTORY);
    }

    @Override
    public final void connect() {
        synchronized (lock) {
            if (state != ConnectionState.CLOSED) {
                throw new IllegalStateException("connect() is only allowed when state is CLOSED but was " + state);
            }
            cancelPendingReconnect();
            userClosed = false;
            reconnectAttempt = 0;
            state = ConnectionState.CONNECTING;
            startTransport();
        }
    }

    @Override
    public final void close() {
        closeInternal(NORMAL_CLOSURE, null);
    }

    @Override
    public final void close(int code, String reason) {
        closeInternal(WebSocketValidator.requireCloseCode(code), reason);
    }

    @Override
    public final void send(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        requireOpen("send(String)");
        try {
            doSendText(text);
        } catch (RuntimeException e) {
            throw connectionFailure(e);
        }
    }

    @Override
    public final void send(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        requireOpen("send(byte[])");
        try {
            doSendBinary(bytes);
        } catch (RuntimeException e) {
            throw connectionFailure(e);
        }
    }

    @Override
    public final void send(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload can not be null");
        }
        requireOpen("send(Object)");
        Message message = MessageCodecs.encode(config.codec(), payload);
        if (message.isText()) {
            try {
                doSendText(message.asText());
            } catch (RuntimeException e) {
                throw connectionFailure(e);
            }
        } else {
            try {
                doSendBinary(message.asBytes());
            } catch (RuntimeException e) {
                throw connectionFailure(e);
            }
        }
    }

    @Override
    public final ConnectionState state() {
        return state;
    }

    @Override
    public final boolean isOpen() {
        return state == ConnectionState.OPEN;
    }

    @Override
    public final String remoteAddress() {
        return doRemoteAddress();
    }

    @Override
    public final String backendType() {
        return WebSocketBackend.TYPE;
    }

    // ─────────── 传输回调回灌点：由实现层在底层回调内调用 ───────────

    /**
     * 回灌「握手成功」事件：{@code CONNECTING → OPEN}，复位重连计数与最近错误，并触发 {@code onOpen}。
     *
     * <p>非 {@code CONNECTING} 时静默忽略（通常是本模块已主动关闭后的迟到回调）。</p>
     */
    protected final void handleOpen() {
        synchronized (lock) {
            if (state != ConnectionState.CONNECTING) {
                return;
            }
            state = ConnectionState.OPEN;
            reconnectAttempt = 0;
            lastError = null;
        }
        ListenerGuard.onOpen(listener);
    }

    /**
     * 回灌「收到数据帧」事件；仅当状态为 {@code OPEN} 时上浮，否则丢弃。
     *
     * @param message 收到的消息
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     */
    protected final void handleMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message can not be null");
        }
        if (state != ConnectionState.OPEN) {
            return;
        }
        ListenerGuard.onMessage(listener, message);
    }

    /**
     * 回灌「连接结束」事件：收敛到 {@code CLOSED}，触发一次 {@code onClose}，随后按自动重连规则决定是否重连。
     *
     * <p>同一连接内重复回灌只生效一次：状态已是 {@code CLOSED} 时直接返回，从而保证 {@code onClose} 恰好一次。</p>
     *
     * @param code 关闭码；无关闭帧时为 {@code -1}
     * @param reason 关闭原因
     * @param remote 是否由对端发起
     */
    protected final void handleClose(int code, String reason, boolean remote) {
        boolean notify;
        synchronized (lock) {
            notify = state != ConnectionState.CLOSED;
            state = ConnectionState.CLOSED;
        }
        if (!notify) {
            return;
        }
        ListenerGuard.onClose(listener, code, reason, remote);
        maybeReconnect();
    }

    /**
     * 回灌「运行态故障」事件：包装后转投 {@code onError}，并记下作为重连耗尽时的最近错误。
     *
     * @param error 故障；已是 {@link WebSocketConnectionException} 时原样上浮，否则包装
     * @throws IllegalArgumentException {@code error} 为 {@code null}
     */
    protected final void handleError(Throwable error) {
        if (error == null) {
            throw new IllegalArgumentException("error can not be null");
        }
        WebSocketConnectionException wrapped = error instanceof WebSocketConnectionException
                ? (WebSocketConnectionException) error
                : new WebSocketConnectionException("web socket transport failure", error);
        lastError = wrapped;
        ListenerGuard.onError(listener, wrapped);
    }

    // ─────────── 传输钩子：由实现层实现（第三方类型只允许出现在实现包） ───────────

    /**
     * 建立一条全新的传输连接，并异步发起建连；每次调用都必须新建传输对象。
     *
     * <p>本方法在 {@code CONNECTING} 状态下被调用：首次建连与自动重连都经此入口。建连结果须经
     * {@link #handleOpen()} / {@link #handleClose(int, String, boolean)} / {@link #handleError(Throwable)}
     * 回灌。同步失败可直接抛出运行时异常，本骨架会将其视为建连失败。
     */
    protected abstract void doConnect();

    /**
     * 通过当前传输发送一条文本消息；仅在状态为 {@code OPEN} 时被调用。
     *
     * @param text 文本内容
     */
    protected abstract void doSendText(String text);

    /**
     * 通过当前传输发送一条二进制消息；仅在状态为 {@code OPEN} 时被调用。
     *
     * @param bytes 二进制内容
     */
    protected abstract void doSendBinary(byte[] bytes);

    /**
     * 关闭当前传输或中止正在进行的建连；幂等由本骨架保证，实现内无需再判重。
     *
     * <p>处于 {@code CONNECTING} 时也必须尽力中止建连（例如关闭尚未完成的传输对象）。</p>
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     */
    protected abstract void doClose(int code, String reason);

    /**
     * 返回当前传输的远端地址字符串；未连接或不可用时返回 {@code null}。
     *
     * @return 远端地址；不可用时 {@code null}
     */
    protected abstract String doRemoteAddress();

    /**
     * 返回构建时使用的配置；供实现读取地址、请求头、超时与心跳参数。
     *
     * @return 客户端配置
     */
    protected final ClientConfig config() {
        return config;
    }

    // ─────────── 内部实现 ───────────

    /**
     * 发起一次建连；调用前状态须已为 {@code CONNECTING}。同步失败按「建连失败」收敛。
     */
    private void startTransport() {
        try {
            doConnect();
        } catch (RuntimeException e) {
            handleError(e);
            handleClose(NO_STATUS_CODE, null, false);
        }
    }

    /**
     * 由重连调度线程发起的一次重连；用户已主动关闭或状态已非 {@code CLOSED} 时放弃本次重连。
     */
    private void reconnect() {
        synchronized (lock) {
            if (userClosed || state != ConnectionState.CLOSED) {
                return;
            }
            state = ConnectionState.CONNECTING;
            startTransport();
        }
    }

    /**
     * 关闭主流程：置用户主动关闭标记、取消待定重连、按当前状态收敛，并保证 {@code onClose} 恰好一次。
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     */
    private void closeInternal(int code, String reason) {
        boolean wasConnecting;
        synchronized (lock) {
            userClosed = true;
            cancelPendingReconnect();
            if (state == ConnectionState.CLOSED || state == ConnectionState.CLOSING) {
                return;
            }
            wasConnecting = state == ConnectionState.CONNECTING;
            if (!wasConnecting) {
                state = ConnectionState.CLOSING;
            }
        }
        try {
            doClose(code, reason);
        } catch (RuntimeException e) {
            handleError(e);
            if (!wasConnecting) {
                handleClose(NO_STATUS_CODE, null, false);
                return;
            }
        }
        if (wasConnecting) {
            // 中止建连：本模块主动收敛，底层未必再回调 onClose
            handleClose(code, reason, false);
        }
    }

    /**
     * 依自动重连规则决定是否安排下一次重连：用户主动关闭或未开启自动重连时不重连；达到上限时进入终态并
     * 触发 {@code onReconnectExhausted}；否则递增计数、算出退避、触发 {@code onReconnect} 并调度。
     */
    private void maybeReconnect() {
        boolean exhausted;
        int attempt = 0;
        long delayMillis = 0L;
        synchronized (lock) {
            if (userClosed || !config.autoReconnect()) {
                return;
            }
            if (reconnectAttempt >= config.maxRetries()) {
                exhausted = true;
            } else {
                exhausted = false;
                reconnectAttempt++;
                attempt = reconnectAttempt;
                delayMillis = Backoff.delayMillis(attempt, config.initialRetryDelayMillis(),
                        config.maxRetryDelayMillis(), config.retryJitterRatio(),
                        ThreadLocalRandom.current().nextDouble());
            }
        }
        if (exhausted) {
            ListenerGuard.onReconnectExhausted(listener, config.maxRetries(), lastError);
        } else {
            ListenerGuard.onReconnect(listener, attempt, delayMillis);
            scheduleReconnect(delayMillis);
        }
    }

    /**
     * 在自有调度线程上延迟发起重连；用户已主动关闭时不再调度。
     *
     * @param delayMillis 退避时长（毫秒）
     */
    private void scheduleReconnect(long delayMillis) {
        synchronized (lock) {
            if (userClosed) {
                return;
            }
            pendingReconnect = scheduler.schedule(new Runnable() {

                @Override
                public void run() {
                    reconnect();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 取消待定的重连任务；须在持有 {@link #lock} 时调用。
     */
    private void cancelPendingReconnect() {
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
            pendingReconnect = null;
        }
    }

    /**
     * 校验当前状态为 {@code OPEN}。
     *
     * @param operation 操作名，用于异常消息定位
     * @throws IllegalStateException 当前状态非 {@code OPEN}
     */
    private void requireOpen(String operation) {
        if (state != ConnectionState.OPEN) {
            throw new IllegalStateException(operation + " is only allowed when state is OPEN but was " + state);
        }
    }

    /**
     * 把传输层异常包装为 {@link WebSocketConnectionException}；已是该类型时原样返回，避免重复包装。
     *
     * @param cause 传输层异常
     * @return 运行态故障
     */
    private WebSocketConnectionException connectionFailure(RuntimeException cause) {
        if (cause instanceof WebSocketConnectionException) {
            return (WebSocketConnectionException) cause;
        }
        return new WebSocketConnectionException("web socket transport failure", cause);
    }
}
