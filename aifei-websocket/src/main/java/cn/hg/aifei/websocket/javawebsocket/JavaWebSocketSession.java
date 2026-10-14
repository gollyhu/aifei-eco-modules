/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.javawebsocket;

import cn.hg.aifei.websocket.ConnectionState;
import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketConnectionException;
import cn.hg.aifei.websocket.WebSocketSession;
import cn.hg.aifei.websocket.internal.MessageCodecs;
import cn.hg.aifei.websocket.internal.SessionIds;
import cn.hg.aifei.websocket.internal.WebSocketValidator;
import cn.hg.aifei.websocket.spi.MessageCodec;
import org.java_websocket.WebSocket;

/**
 * 服务端侧会话的 Java-WebSocket 实现：包装底层 {@link WebSocket} 连接，把收发与关闭翻译为本模块契约。
 *
 * <p>会话标识在构造时由 {@link SessionIds} 生成，连接存续期间稳定；状态经
 * {@link JavaWebSocketSupport#toConnectionState(org.java_websocket.enums.ReadyState)} 映射，保证与客户端
 * 同源。{@code send*} 仅在 {@code OPEN} 时成功，否则抛 {@link IllegalStateException}；关闭码经
 * {@link WebSocketValidator#requireCloseCode(int)} 校验。本类方法可从任意线程调用，也允许在服务端回调内
 * 调用（回声写法安全）。</p>
 */
public final class JavaWebSocketSession implements WebSocketSession {

    private final WebSocket connection;

    /**
     * 服务端配置的编解码器；{@code null} 表示未配置对象编解码。
     */
    private final MessageCodec codec;

    private final String id = SessionIds.next();

    /**
     * 包装一条底层连接为本模块会话。
     *
     * @param connection 底层连接；须为非 {@code null}
     * @param codec 服务端配置的编解码器；可为 {@code null}
     */
    JavaWebSocketSession(WebSocket connection, MessageCodec codec) {
        this.connection = connection;
        this.codec = codec;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ConnectionState state() {
        return JavaWebSocketSupport.toConnectionState(connection.getReadyState());
    }

    @Override
    public boolean isOpen() {
        return state() == ConnectionState.OPEN;
    }

    @Override
    public String remoteAddress() {
        return JavaWebSocketSupport.formatAddress(connection.getRemoteSocketAddress());
    }

    @Override
    public void send(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        requireOpen("send(String)");
        try {
            connection.send(text);
        } catch (RuntimeException e) {
            throw connectionFailure(e);
        }
    }

    @Override
    public void send(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        requireOpen("send(byte[])");
        try {
            connection.send(bytes);
        } catch (RuntimeException e) {
            throw connectionFailure(e);
        }
    }

    @Override
    public void send(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload can not be null");
        }
        requireOpen("send(Object)");
        Message message = MessageCodecs.encode(codec, payload);
        if (message.isText()) {
            send(message.asText());
        } else {
            send(message.asBytes());
        }
    }

    @Override
    public void close() {
        closeWith(JavaWebSocketSupport.NORMAL_CLOSURE, null);
    }

    @Override
    public void close(int code, String reason) {
        closeWith(WebSocketValidator.requireCloseCode(code), reason);
    }

    /**
     * 关闭主流程：已关闭时为无操作（幂等），否则发关闭帧。
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     */
    private void closeWith(int code, String reason) {
        if (state() == ConnectionState.CLOSED) {
            return;
        }
        connection.close(code, reason == null ? "" : reason);
    }

    /**
     * 校验当前状态为 {@code OPEN}。
     *
     * @param operation 操作名，用于异常消息定位
     * @throws IllegalStateException 当前状态非 {@code OPEN}
     */
    private void requireOpen(String operation) {
        ConnectionState current = state();
        if (current != ConnectionState.OPEN) {
            throw new IllegalStateException(operation + " is only allowed when state is OPEN but was " + current);
        }
    }

    /**
     * 把传输层异常包装为 {@link WebSocketConnectionException}；已是该类型时原样返回，避免重复包装，且不
     * 外泄底层异常类型名。
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
