/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.ServerConfig;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketConnectionException;
import cn.hg.aifei.websocket.WebSocketException;
import cn.hg.aifei.websocket.WebSocketServer;
import cn.hg.aifei.websocket.WebSocketSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 跨后端共享的服务端骨架：会话登记与注销、广播、空闲会话回收调度、启停幂等都在此实现一次。
 *
 * <p>传输后端只实现启停与端口三个钩子：{@link #doStart()} / {@link #doStop()} / {@link #doPort()}。会话事件经
 * {@link #handleSessionOpen(WebSocketSession)} / {@link #handleSessionMessage(WebSocketSession, Message)} /
 * {@link #refreshSessionActivity(WebSocketSession)} / {@link #handleSessionClose(WebSocketSession, int, String, boolean)} /
 * {@link #handleError(WebSocketSession, Throwable)} 回灌本骨架。会话登记、广播跳过规则、空闲回收与回调保护
 * 各自只有一处实现，后端不得复制。</p>
 *
 * <p>线程安全：公共方法可从任意线程调用。会话登记表由内部锁保护，{@code sessions()} 返回不可变快照；
 * 空闲回收在私有的单线程守护调度器上执行；启停由独立的生命周期锁串行化，避免 {@code doStop()} 内部等待
 * 会话关闭回调时与登记锁成环。</p>
 */
public abstract class AbstractWebSocketServer implements WebSocketServer {

    /**
     * RFC6455 正常关闭码，用于空闲会话回收。
     */
    private static final int NORMAL_CLOSURE = 1000;

    /**
     * 空闲回收线程工厂：线程为守护线程，随 JVM 退出而终止。
     */
    private static final ThreadFactory IDLE_THREAD_FACTORY = new ThreadFactory() {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "aifei-websocket-idle-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    };

    private final ServerConfig config;

    private final ServerListener listener;

    /**
     * 保护会话登记表 {@link #sessions} 的内部锁。
     */
    private final Object lock = new Object();

    /**
     * 串行化启停的生命周期锁；与 {@link #lock} 相互独立，避免与关闭回调成环。
     */
    private final Object lifecycleLock = new Object();

    /**
     * 已登记会话，按登记顺序保存；受 {@link #lock} 保护。
     */
    private final Map<String, SessionEntry> sessions = new LinkedHashMap<>();

    private final ScheduledExecutorService scheduler;

    private volatile boolean started;

    /**
     * 空闲回收任务句柄；未启用或已停止时为 {@code null}。
     */
    private volatile ScheduledFuture<?> idleScan;

    /**
     * 保存装配输入。
     *
     * @param config 服务端配置
     * @param listener 服务端监听器
     * @throws IllegalArgumentException {@code config} 或 {@code listener} 为 {@code null}
     */
    protected AbstractWebSocketServer(ServerConfig config, ServerListener listener) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        this.config = config;
        this.listener = listener;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(IDLE_THREAD_FACTORY);
    }

    @Override
    public final void start() {
        synchronized (lifecycleLock) {
            if (started) {
                throw new IllegalStateException("server is already started");
            }
            try {
                doStart();
            } catch (WebSocketException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new WebSocketException("failed to start server", e);
            }
            started = true;
            scheduleIdleScan();
        }
        ListenerGuard.onStart(listener);
    }

    @Override
    public final void stop() {
        synchronized (lifecycleLock) {
            if (!started) {
                return;
            }
            started = false;
            cancelIdleScan();
            try {
                doStop();
            } catch (WebSocketException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new WebSocketException("failed to stop server", e);
            }
        }
        ListenerGuard.onStop(listener);
    }

    @Override
    public final boolean isStarted() {
        return started;
    }

    @Override
    public final int port() {
        if (!started) {
            return 0;
        }
        return doPort();
    }

    @Override
    public final Collection<WebSocketSession> sessions() {
        synchronized (lock) {
            List<WebSocketSession> snapshot = new ArrayList<>(sessions.size());
            for (SessionEntry entry : sessions.values()) {
                snapshot.add(entry.session);
            }
            return Collections.unmodifiableList(snapshot);
        }
    }

    @Override
    public final void broadcast(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        broadcastMessage(Message.text(text));
    }

    @Override
    public final void broadcast(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        broadcastMessage(Message.binary(bytes));
    }

    @Override
    public final void broadcast(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload can not be null");
        }
        broadcastMessage(MessageCodecs.encode(config.codec(), payload));
    }

    @Override
    public final String backendType() {
        return config.type();
    }

    // ─────────── 会话事件回灌点：由实现层在底层回调内调用 ───────────

    /**
     * 回灌「会话可收发」事件：登记会话并触发 {@code onOpen}。会话标识由实现层经
     * {@link SessionIds#next()} 生成后传入。
     *
     * @param session 新会话
     * @throws IllegalArgumentException {@code session} 为 {@code null}
     */
    protected final void handleSessionOpen(WebSocketSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        synchronized (lock) {
            sessions.put(session.id(), new SessionEntry(session, System.currentTimeMillis()));
        }
        ListenerGuard.onOpen(listener, session);
    }

    /**
     * 回灌「会话收到数据帧」事件：刷新活跃时间并触发 {@code onMessage}。
     *
     * @param session 来源会话
     * @param message 收到的消息
     * @throws IllegalArgumentException {@code session} 或 {@code message} 为 {@code null}
     */
    protected final void handleSessionMessage(WebSocketSession session, Message message) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        if (message == null) {
            throw new IllegalArgumentException("message can not be null");
        }
        refreshActivity(session);
        ListenerGuard.onMessage(listener, session, message);
    }

    /**
     * 回灌「会话有入站活动」事件：把无需上浮为 {@link Message} 的入站帧（如 pong 控制帧）计入活跃时间，
     * 供空闲回收判活。
     *
     * @param session 相关会话
     * @throws IllegalArgumentException {@code session} 为 {@code null}
     */
    protected final void refreshSessionActivity(WebSocketSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        refreshActivity(session);
    }

    /**
     * 回灌「会话关闭」事件：先触发 {@code onClose}（此时 {@code session} 仍可读标识与远端地址），再注销会话。
     *
     * @param session 被关闭的会话
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote 是否由对端发起
     * @throws IllegalArgumentException {@code session} 为 {@code null}
     */
    protected final void handleSessionClose(WebSocketSession session, int code, String reason, boolean remote) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        ListenerGuard.onClose(listener, session, code, reason, remote);
        synchronized (lock) {
            sessions.remove(session.id());
        }
    }

    /**
     * 回灌「运行态故障」事件：包装后转投 {@code onError}。
     *
     * @param session 相关会话；服务器级故障时为 {@code null}
     * @param error 故障；已是 {@link WebSocketException} 时原样上浮，否则包装
     * @throws IllegalArgumentException {@code error} 为 {@code null}
     */
    protected final void handleError(WebSocketSession session, Throwable error) {
        if (error == null) {
            throw new IllegalArgumentException("error can not be null");
        }
        WebSocketConnectionException wrapped = error instanceof WebSocketConnectionException
                ? (WebSocketConnectionException) error
                : new WebSocketConnectionException("web socket transport failure", error);
        ListenerGuard.onError(listener, session, wrapped);
    }

    // ─────────── 传输钩子：由实现层实现（第三方类型只允许出现在实现包） ───────────

    /**
     * 绑定并在配置端口上开始接受连接；返回后 {@link #doPort()} 必须可返回实际绑定端口且端口可被接受连接。
     *
     * <p>绑定失败等运行态故障应抛 {@link WebSocketException}（或让本骨架包装）。已启动时的重复调用由本
     * 骨架拦截，实现无需判重。</p>
     */
    protected abstract void doStart();

    /**
     * 停止接受新连接、向全部会话发出关闭帧（关闭码 {@code 1001}，即 RFC6455 的 GOING_AWAY）并释放监听端口；实现须尽力
     * 保证每个会话的关闭都会经 {@link #handleSessionClose(WebSocketSession, int, String, boolean)} 回灌。
     */
    protected abstract void doStop();

    /**
     * 返回实际绑定的监听端口；仅在已启动时被调用。
     *
     * @return 实际监听端口
     */
    protected abstract int doPort();

    /**
     * 返回构建时使用的配置；供实现读取监听地址、端口与连接参数。
     *
     * @return 服务端配置
     */
    protected final ServerConfig config() {
        return config;
    }

    // ─────────── 内部实现 ───────────

    /**
     * 刷新会话活跃时间；未登记会话静默跳过。
     *
     * @param session 相关会话
     */
    private void refreshActivity(WebSocketSession session) {
        synchronized (lock) {
            SessionEntry entry = sessions.get(session.id());
            if (entry != null) {
                entry.lastActivityMillis = System.currentTimeMillis();
            }
        }
    }

    /**
     * 依消息类型向全部已登记会话广播；对已关闭会话与广播瞬间关闭的会话静默跳过，不中断其余会话。
     *
     * @param message 待广播消息
     */
    private void broadcastMessage(Message message) {
        for (WebSocketSession session : sessions()) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                if (message.isText()) {
                    session.send(message.asText());
                } else {
                    session.send(message.asBytes());
                }
            } catch (RuntimeException ignored) {
                // 会话可能正在关闭，广播跳过该会话，不中断其余会话
            }
        }
    }

    /**
     * 按 {@code max(1, sessionIdleTimeoutSeconds)} 秒周期安排空闲会话回收；阈值为 {@code 0} 时不启用。
     */
    private void scheduleIdleScan() {
        int idleSeconds = config.sessionIdleTimeoutSeconds();
        if (idleSeconds <= 0) {
            return;
        }
        long periodSeconds = Math.max(1, idleSeconds);
        idleScan = scheduler.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                recycleIdleSessions();
            }
        }, periodSeconds, periodSeconds, TimeUnit.SECONDS);
    }

    /**
     * 取消空闲回收任务。
     */
    private void cancelIdleScan() {
        ScheduledFuture<?> scan = idleScan;
        if (scan != null) {
            scan.cancel(false);
            idleScan = null;
        }
    }

    /**
     * 扫描并回收空闲会话：空闲时长达到阈值者先移出登记表，再以关闭码 {@code 1000} 关闭；关闭回调随后仍会
     * 触发一次 {@code onClose}。
     */
    private void recycleIdleSessions() {
        long thresholdMillis = config.sessionIdleTimeoutSeconds() * 1000L;
        if (thresholdMillis <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        List<WebSocketSession> idle = new ArrayList<>();
        synchronized (lock) {
            Iterator<SessionEntry> iterator = sessions.values().iterator();
            while (iterator.hasNext()) {
                SessionEntry entry = iterator.next();
                if (now - entry.lastActivityMillis >= thresholdMillis) {
                    iterator.remove();
                    idle.add(entry.session);
                }
            }
        }
        for (WebSocketSession session : idle) {
            try {
                session.close(NORMAL_CLOSURE, "session idle timeout");
            } catch (RuntimeException ignored) {
                // 会话可能已自行关闭，回收静默跳过
            }
        }
    }

    /**
     * 会话登记条目：保存会话与最近一次入站活动时间，后者由空闲回收判活。
     */
    private static final class SessionEntry {

        private final WebSocketSession session;

        /**
         * 最近一次入站帧时间（毫秒）；受 {@link AbstractWebSocketServer#lock} 保护。
         */
        private long lastActivityMillis;

        private SessionEntry(WebSocketSession session, long lastActivityMillis) {
            this.session = session;
            this.lastActivityMillis = lastActivityMillis;
        }
    }
}
