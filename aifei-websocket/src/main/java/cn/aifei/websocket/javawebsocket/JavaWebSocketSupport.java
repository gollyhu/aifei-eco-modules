/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.javawebsocket;

import cn.aifei.websocket.ConnectionState;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import org.java_websocket.enums.ReadyState;

/**
 * Java-WebSocket 后端的适配工具：把底层类型翻译成本模块公共类型，是这些翻译规则的唯一定义点。
 *
 * <p>状态映射（{@link ReadyState} → {@link ConnectionState}）、二进制载荷拷贝（{@link ByteBuffer} →
 * {@code byte[]}）与地址格式化都只在此实现一次，客户端、服务端与会话实现共用，避免各自复制。</p>
 *
 * <p>本类只被本实现包内部使用，方法均为静态纯函数，可从任意线程调用。</p>
 */
public final class JavaWebSocketSupport {

    /**
     * RFC6455 正常关闭码。
     */
    public static final int NORMAL_CLOSURE = 1000;

    /**
     * 禁止创建工具类实例。
     */
    private JavaWebSocketSupport() {
    }

    /**
     * 把底层就绪状态映射为本模块连接状态。
     *
     * <p>底层 {@code NOT_YET_CONNECTED} 对应握手进行中，故映射为 {@code CONNECTING}；其余一一对应。
     * 未知或 {@code null} 一律映射为 {@code CLOSED}。</p>
     *
     * @param readyState 底层就绪状态
     * @return 本模块连接状态
     */
    public static ConnectionState toConnectionState(ReadyState readyState) {
        if (readyState == null) {
            return ConnectionState.CLOSED;
        }
        switch (readyState) {
            case NOT_YET_CONNECTED:
                return ConnectionState.CONNECTING;
            case OPEN:
                return ConnectionState.OPEN;
            case CLOSING:
                return ConnectionState.CLOSING;
            case CLOSED:
            default:
                return ConnectionState.CLOSED;
        }
    }

    /**
     * 把二进制帧载荷拷贝为字节数组。
     *
     * @param buffer 二进制帧载荷
     * @return 载荷内容副本
     * @throws IllegalArgumentException {@code buffer} 为 {@code null}
     */
    public static byte[] toBytes(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("buffer can not be null");
        }
        ByteBuffer view = buffer.duplicate();
        byte[] bytes = new byte[view.remaining()];
        view.get(bytes);
        return bytes;
    }

    /**
     * 把远端套接字地址格式化为 {@code host:port} 字符串。
     *
     * @param address 远端地址；不可用时为 {@code null}
     * @return 地址字符串；{@code address} 为 {@code null} 时返回 {@code null}
     */
    public static String formatAddress(InetSocketAddress address) {
        if (address == null) {
            return null;
        }
        String host = address.getAddress() != null
                ? address.getAddress().getHostAddress()
                : address.getHostString();
        return host + ":" + address.getPort();
    }
}
