/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.internal;

import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.Message;
import cn.aifei.websocket.ServerListener;
import cn.aifei.websocket.WebSocketSession;

/**
 * 回调保护的唯一定义点：监听器回调内抛出的任何错误都被捕获并转投该监听器的 {@code onError}，
 * 不向传输线程传播，从而保证一次回调失败不影响后续回调与底层 I/O。
 *
 * <p>客户端与服务端两套监听器各有一组入口。转投 {@code onError} 时若 {@code onError} 自身再抛错，
 * 已无处可投，只能就地吞掉（本模块内部无日志依赖，不落日志）。钩子按参数类型区分两套监听器。</p>
 */
public final class ListenerGuard {

    /**
     * 禁止创建工具类实例。
     */
    private ListenerGuard() {
    }

    /**
     * 受保护地触发客户端 {@code onOpen}。
     *
     * @param listener 客户端监听器
     */
    public static void onOpen(ClientListener listener) {
        try {
            listener.onOpen();
        } catch (Throwable error) {
            onError(listener, error);
        }
    }

    /**
     * 受保护地触发客户端 {@code onMessage}。
     *
     * @param listener 客户端监听器
     * @param message 收到的消息
     */
    public static void onMessage(ClientListener listener, Message message) {
        try {
            listener.onMessage(message);
        } catch (Throwable error) {
            onError(listener, error);
        }
    }

    /**
     * 受保护地触发客户端 {@code onClose}。
     *
     * @param listener 客户端监听器
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote 是否由对端发起
     */
    public static void onClose(ClientListener listener, int code, String reason, boolean remote) {
        try {
            listener.onClose(code, reason, remote);
        } catch (Throwable error) {
            onError(listener, error);
        }
    }

    /**
     * 受保护地触发客户端 {@code onError}；本方法自身不再转投，捕获后直接吞掉。
     *
     * @param listener 客户端监听器
     * @param error 故障
     */
    public static void onError(ClientListener listener, Throwable error) {
        try {
            listener.onError(error);
        } catch (Throwable ignored) {
            // onError 已是最末端的故障出口，再抛错只能吞掉以保护传输线程
        }
    }

    /**
     * 受保护地触发客户端 {@code onReconnect}。
     *
     * @param listener 客户端监听器
     * @param attempt 重连次数
     * @param delayMillis 本次退避时长（毫秒）
     */
    public static void onReconnect(ClientListener listener, int attempt, long delayMillis) {
        try {
            listener.onReconnect(attempt, delayMillis);
        } catch (Throwable error) {
            onError(listener, error);
        }
    }

    /**
     * 受保护地触发客户端 {@code onReconnectExhausted}。
     *
     * @param listener 客户端监听器
     * @param attempts 已连续失败的次数
     * @param lastError 最后一次失败的原因
     */
    public static void onReconnectExhausted(ClientListener listener, int attempts, Throwable lastError) {
        try {
            listener.onReconnectExhausted(attempts, lastError);
        } catch (Throwable error) {
            onError(listener, error);
        }
    }

    /**
     * 受保护地触发服务端 {@code onOpen}。
     *
     * @param listener 服务端监听器
     * @param session 新会话
     */
    public static void onOpen(ServerListener listener, WebSocketSession session) {
        try {
            listener.onOpen(session);
        } catch (Throwable error) {
            onError(listener, session, error);
        }
    }

    /**
     * 受保护地触发服务端 {@code onMessage}。
     *
     * @param listener 服务端监听器
     * @param session 来源会话
     * @param message 收到的消息
     */
    public static void onMessage(ServerListener listener, WebSocketSession session, Message message) {
        try {
            listener.onMessage(session, message);
        } catch (Throwable error) {
            onError(listener, session, error);
        }
    }

    /**
     * 受保护地触发服务端 {@code onClose}。
     *
     * @param listener 服务端监听器
     * @param session 被关闭的会话
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote 是否由对端发起
     */
    public static void onClose(ServerListener listener, WebSocketSession session, int code, String reason, boolean remote) {
        try {
            listener.onClose(session, code, reason, remote);
        } catch (Throwable error) {
            onError(listener, session, error);
        }
    }

    /**
     * 受保护地触发服务端 {@code onStart}。
     *
     * @param listener 服务端监听器
     */
    public static void onStart(ServerListener listener) {
        try {
            listener.onStart();
        } catch (Throwable error) {
            onError(listener, null, error);
        }
    }

    /**
     * 受保护地触发服务端 {@code onStop}。
     *
     * @param listener 服务端监听器
     */
    public static void onStop(ServerListener listener) {
        try {
            listener.onStop();
        } catch (Throwable error) {
            onError(listener, null, error);
        }
    }

    /**
     * 受保护地触发服务端 {@code onError}；本方法自身不再转投，捕获后直接吞掉。
     *
     * @param listener 服务端监听器
     * @param session 相关会话；服务器级故障时为 {@code null}
     * @param error 故障
     */
    public static void onError(ServerListener listener, WebSocketSession session, Throwable error) {
        try {
            listener.onError(session, error);
        } catch (Throwable ignored) {
            // onError 已是最末端的故障出口，再抛错只能吞掉以保护传输线程
        }
    }
}
