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
 * 跨后端共享的服务端骨架：会话登记、广播、空闲回收与启停幂等在此实现一次。
 *
 * <p>会话登记、广播跳过规则、空闲回收与回调保护各自只有一处实现。会话表由内部锁保护，sessions() 返回
 * 不可变快照；启停用独立生命周期锁串行化，避免 doStop() 等待关闭回调时与登记锁成环。</p>
 */
public abstract class AbstractWebSocketServer implements WebSocketServer {

    private static final int NORMAL_CLOSURE = 1000;

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

    /** 与 lock 独立，避免 doStop() 等待关闭回调时成环。 */
    private final Object lifecycleLock = new Object();

    /**
     * 已登记会话，按登记顺序保存；受 {@link #lock} 保护。
     */
    private final Map<String, SessionEntry> sessions = new LinkedHashMap<>();

    /** 类级共享且永不关闭：stop() 后 start() 仍须能回收空闲会话（硬契约）。 */
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(IDLE_THREAD_FACTORY);

    private volatile boolean started;

    /**
     * 空闲回收任务句柄；未启用或已停止时为 {@code null}。
     */
    private volatile ScheduledFuture<?> idleScan;

    /**
     * 保存装配输入。
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
        return WebSocketBackend.TYPE;
    }

    /** 登记会话并触发 onOpen；session.id() 须由实现层经 {@link SessionIds#next()} 生成。 */
    protected final void handleSessionOpen(WebSocketSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        synchronized (lock) {
            sessions.put(session.id(), new SessionEntry(session, System.currentTimeMillis()));
        }
        ListenerGuard.onOpen(listener, session);
    }

    /** 刷新活跃时间并触发 onMessage。 */
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

    /** 把 pong 等不上浮的入站帧计入活跃，避免只回 pong 的存活会话被空闲回收误判。 */
    protected final void refreshSessionActivity(WebSocketSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        refreshActivity(session);
    }

    /** 先触发 onClose（此时仍可读标识与地址），再注销会话。 */
    protected final void handleSessionClose(WebSocketSession session, int code, String reason, boolean remote) {
        if (session == null) {
            throw new IllegalArgumentException("session can not be null");
        }
        ListenerGuard.onClose(listener, session, code, reason, remote);
        synchronized (lock) {
            sessions.remove(session.id());
        }
    }

    /** 包装故障后转投 onError；session 为 null 表示服务器级故障。 */
    protected final void handleError(WebSocketSession session, Throwable error) {
        if (error == null) {
            throw new IllegalArgumentException("error can not be null");
        }
        WebSocketConnectionException wrapped = error instanceof WebSocketConnectionException
                ? (WebSocketConnectionException) error
                : new WebSocketConnectionException("web socket transport failure", error);
        ListenerGuard.onError(listener, session, wrapped);
    }

    /** 绑定并接受连接；绑定失败抛 {@link WebSocketException}，返回后 doPort() 须可读实际端口。 */
    protected abstract void doStart();

    /** 停止接受连接并向全部会话发关闭帧（{@code 1001} GOING_AWAY）；每个关闭须经 handleSessionClose 回灌。 */
    protected abstract void doStop();

    /** 实际监听端口；仅在已启动时调用。 */
    protected abstract int doPort();

    /** 构建时使用的配置。 */
    protected final ServerConfig config() {
        return config;
    }

    /** 刷新活跃时间；未登记会话静默跳过。 */
    private void refreshActivity(WebSocketSession session) {
        synchronized (lock) {
            SessionEntry entry = sessions.get(session.id());
            if (entry != null) {
                entry.lastActivityMillis = System.currentTimeMillis();
            }
        }
    }

    /** 向全部会话广播，跳过已关闭者；二进制副本在循环外只取一次以避免逐会话克隆。 */
    private void broadcastMessage(Message message) {
        byte[] bytes = message.isText() ? null : message.asBytes();
        for (WebSocketSession session : sessions()) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                if (message.isText()) {
                    session.send(message.asText());
                } else {
                    session.send(bytes);
                }
            } catch (RuntimeException ignored) {
                // 会话可能正在关闭，广播跳过该会话，不中断其余会话
            }
        }
    }

    /** 按 max(1, sessionIdleTimeoutSeconds) 秒周期安排空闲回收；阈值为 0 时不启用。 */
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

    /** 取消空闲回收任务。 */
    private void cancelIdleScan() {
        ScheduledFuture<?> scan = idleScan;
        if (scan != null) {
            scan.cancel(false);
            idleScan = null;
        }
    }

    /** 回收空闲会话：先移出登记表再以关闭码 1000 关闭；关闭回调随后仍触发一次 onClose。 */
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

    /** 会话登记条目：保存会话与最近一次入站活动时间，后者由空闲回收判活。 */
    private static final class SessionEntry {

        private final WebSocketSession session;

        /** 最近一次入站帧时间（毫秒）；受 {@link AbstractWebSocketServer#lock} 保护。 */
        private long lastActivityMillis;

        private SessionEntry(WebSocketSession session, long lastActivityMillis) {
            this.session = session;
            this.lastActivityMillis = lastActivityMillis;
        }
    }
}
