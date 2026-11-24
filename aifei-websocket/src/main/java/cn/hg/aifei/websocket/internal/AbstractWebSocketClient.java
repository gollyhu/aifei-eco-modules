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
 * 跨后端共享的客户端骨架：状态机、发送校验、编解码、关闭幂等与自动重连编排在此实现一次。
 *
 * <p>后端只实现传输钩子，底层回调经 handle* 回灌本骨架；状态迁移、退避、校验、回调保护各自只有一处实现。
 * 状态以 volatile 发布、迁移在内部锁内完成，重连不经传输回调线程。</p>
 */
public abstract class AbstractWebSocketClient implements WebSocketClient {

    /**
     * RFC6455 正常关闭码。
     */
    private static final int NORMAL_CLOSURE = 1000;

    /** 无关闭帧（连接未建立或异常中断）时上报 -1，与传输层约定一致。 */
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

    /** 类级共享且永不关闭：close() 后显式 connect() 仍须能调度重连（硬契约）。 */
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(RECONNECT_THREAD_FACTORY);

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

    /** 跨字段校验（地址协议、重试延迟上下限）在构建时完成。 */
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

    /** 回灌握手成功：CONNECTING → OPEN；非 CONNECTING 的迟到回调静默忽略。 */
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

    /** 回灌收到的数据帧；仅 OPEN 时上浮。 */
    protected final void handleMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message can not be null");
        }
        if (state != ConnectionState.OPEN) {
            return;
        }
        ListenerGuard.onMessage(listener, message);
    }

    /** 回灌连接结束：收敛到 CLOSED 并按规则重连；重复回灌直接返回，保证 onClose 恰好一次。 */
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

    /** 回灌运行态故障：包装后转投 onError，并记下作为重连耗尽时的最近错误。 */
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

    /** 建连钩子：每次调用必须新建传输对象，结果经 handle* 回灌；同步失败按建连失败收敛。 */
    protected abstract void doConnect();

    /** 发送文本；仅在 OPEN 时被调用。 */
    protected abstract void doSendText(String text);

    /** 发送二进制；仅在 OPEN 时被调用。 */
    protected abstract void doSendBinary(byte[] bytes);

    /** 关闭传输或中止建连；幂等由骨架保证，CONNECTING 时亦须尽力中止（硬契约）。 */
    protected abstract void doClose(int code, String reason);

    /** 远端地址；未连接或不可用时返回 null。 */
    protected abstract String doRemoteAddress();

    /** 构建时使用的配置。 */
    protected final ClientConfig config() {
        return config;
    }

    /** 发起建连；调用前状态须为 CONNECTING，同步失败按建连失败收敛。 */
    private void startTransport() {
        try {
            doConnect();
        } catch (RuntimeException e) {
            handleError(e);
            handleClose(NO_STATUS_CODE, null, false);
        }
    }

    /** 重连调度线程发起；用户已主动关闭或状态非 CLOSED 时放弃。 */
    private void reconnect() {
        synchronized (lock) {
            if (userClosed || state != ConnectionState.CLOSED) {
                return;
            }
            state = ConnectionState.CONNECTING;
            startTransport();
        }
    }

    /** 关闭主流程：置用户关闭标记、取消待定重连、按状态收敛，保证 onClose 恰好一次。 */
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

    /** 依自动重连规则安排下一次重连；用户主动关闭或未开启时不重连，达上限进入终态。 */
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

    /** 在调度线程上延迟发起重连；用户已主动关闭时不再调度。 */
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

    /** 取消待定重连任务；须持 lock 调用。 */
    private void cancelPendingReconnect() {
        if (pendingReconnect != null) {
            pendingReconnect.cancel(false);
            pendingReconnect = null;
        }
    }

    /** 校验当前状态为 OPEN。 */
    private void requireOpen(String operation) {
        if (state != ConnectionState.OPEN) {
            throw new IllegalStateException(operation + " is only allowed when state is OPEN but was " + state);
        }
    }

    /** 包装传输异常；已是 WebSocketConnectionException 时原样返回，避免重复包装。 */
    private WebSocketConnectionException connectionFailure(RuntimeException cause) {
        if (cause instanceof WebSocketConnectionException) {
            return (WebSocketConnectionException) cause;
        }
        return new WebSocketConnectionException("web socket transport failure", cause);
    }
}
