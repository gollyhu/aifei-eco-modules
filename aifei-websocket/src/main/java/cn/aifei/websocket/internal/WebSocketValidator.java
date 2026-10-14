/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.internal;

import java.util.Map;

/**
 * 参数与配置校验的唯一定义点：字段级校验在配置 setter 内调用，跨字段校验在构建实例时调用。
 *
 * <p>全部方法返回校验后的值，便于在赋值处直接使用。越界或非法一律抛 {@link IllegalArgumentException}，
 * 消息只描述约束与实参，不含任何第三方类型名，以守住公共面零第三方类型。</p>
 */
public final class WebSocketValidator {

    /**
     * 心跳与重试次数类字段的上限：对齐以秒为单位的底层连接丢失检测上限。
     */
    private static final int SECONDS_LIMIT = 2147483;

    /**
     * 关闭码取值下界（RFC6455 正常关闭）。
     */
    private static final int CLOSE_CODE_MIN = 1000;

    /**
     * 关闭码取值上界（RFC6455 允许的应用私有区间末端）。
     */
    private static final int CLOSE_CODE_MAX = 4999;

    /**
     * 禁止创建工具类实例。
     */
    private WebSocketValidator() {
    }

    /**
     * 校验传输后端名非空白。
     *
     * @param type 传输后端名
     * @return 校验后的传输后端名
     * @throws IllegalArgumentException {@code type} 为 {@code null} 或空白
     */
    public static String requireType(String type) {
        if (isBlank(type)) {
            throw new IllegalArgumentException("type can not be blank");
        }
        return type;
    }

    /**
     * 校验心跳间隔在 {@code [0, 2147483]} 内；{@code 0} 表示禁用。
     *
     * @param seconds 心跳间隔（秒）
     * @return 校验后的心跳间隔（秒）
     * @throws IllegalArgumentException {@code seconds} 超出 {@code [0, 2147483]}
     */
    public static int requireHeartbeatSeconds(int seconds) {
        if (seconds < 0 || seconds > SECONDS_LIMIT) {
            throw new IllegalArgumentException("heartbeatSeconds must be in [0, " + SECONDS_LIMIT + "] but was " + seconds);
        }
        return seconds;
    }

    /**
     * 校验目标地址非空白；协议是否合法由构建时的 {@link #requireUriScheme(String)} 负责。
     *
     * @param uri 目标地址
     * @return 校验后的目标地址
     * @throws IllegalArgumentException {@code uri} 为 {@code null} 或空白
     */
    public static String requireUri(String uri) {
        if (isBlank(uri)) {
            throw new IllegalArgumentException("uri can not be blank");
        }
        return uri;
    }

    /**
     * 校验目标地址协议为 {@code ws} 或 {@code wss}；跨字段校验，供构建时调用。
     *
     * @param uri 目标地址
     * @return 校验后的目标地址
     * @throws IllegalArgumentException {@code uri} 为空白或协议不是 {@code ws://} / {@code wss://}
     */
    public static String requireUriScheme(String uri) {
        String value = requireUri(uri);
        if (!value.startsWith("ws://") && !value.startsWith("wss://")) {
            throw new IllegalArgumentException("uri must start with ws:// or wss:// but was " + value);
        }
        return value;
    }

    /**
     * 校验连接超时大于 {@code 0}。
     *
     * @param millis 连接超时（毫秒）
     * @return 校验后的连接超时（毫秒）
     * @throws IllegalArgumentException {@code millis} 小于 {@code 1}
     */
    public static int requireConnectTimeoutMillis(int millis) {
        if (millis < 1) {
            throw new IllegalArgumentException("connectTimeoutMillis must be greater than zero but was " + millis);
        }
        return millis;
    }

    /**
     * 校验连续失败重试上限在 {@code [1, 2147483]} 内。
     *
     * @param maxRetries 连续失败重试上限
     * @return 校验后的重试上限
     * @throws IllegalArgumentException {@code maxRetries} 超出 {@code [1, 2147483]}
     */
    public static int requireMaxRetries(int maxRetries) {
        if (maxRetries < 1 || maxRetries > SECONDS_LIMIT) {
            throw new IllegalArgumentException("maxRetries must be in [1, " + SECONDS_LIMIT + "] but was " + maxRetries);
        }
        return maxRetries;
    }

    /**
     * 校验首次重试延迟大于 {@code 0}。
     *
     * @param millis 首次重试延迟（毫秒）
     * @return 校验后的首次重试延迟（毫秒）
     * @throws IllegalArgumentException {@code millis} 小于 {@code 1}
     */
    public static long requireInitialRetryDelayMillis(long millis) {
        if (millis < 1L) {
            throw new IllegalArgumentException("initialRetryDelayMillis must be greater than zero but was " + millis);
        }
        return millis;
    }

    /**
     * 校验单次重试延迟上限不小于首次重试延迟；跨字段校验，供构建时调用。
     *
     * @param initialRetryDelayMillis 首次重试延迟（毫秒）
     * @param maxRetryDelayMillis 单次重试延迟上限（毫秒）
     * @return 校验后的单次重试延迟上限（毫秒）
     * @throws IllegalArgumentException {@code maxRetryDelayMillis} 小于 {@code initialRetryDelayMillis}
     */
    public static long requireMaxRetryDelayMillis(long initialRetryDelayMillis, long maxRetryDelayMillis) {
        if (maxRetryDelayMillis < initialRetryDelayMillis) {
            throw new IllegalArgumentException("maxRetryDelayMillis must be >= initialRetryDelayMillis but was "
                    + maxRetryDelayMillis + " < " + initialRetryDelayMillis);
        }
        return maxRetryDelayMillis;
    }

    /**
     * 校验重试抖动比例在 {@code [0.0, 1.0]} 内。
     *
     * @param ratio 抖动比例
     * @return 校验后的抖动比例
     * @throws IllegalArgumentException {@code ratio} 为 {@code NaN} 或超出 {@code [0.0, 1.0]}
     */
    public static double requireRetryJitterRatio(double ratio) {
        if (Double.isNaN(ratio) || ratio < 0.0 || ratio > 1.0) {
            throw new IllegalArgumentException("retryJitterRatio must be in [0.0, 1.0] but was " + ratio);
        }
        return ratio;
    }

    /**
     * 校验握手附加请求头非 {@code null}。
     *
     * @param headers 请求头
     * @return 校验后的请求头
     * @throws IllegalArgumentException {@code headers} 为 {@code null}
     */
    public static Map<String, String> requireHeaders(Map<String, String> headers) {
        if (headers == null) {
            throw new IllegalArgumentException("headers can not be null");
        }
        return headers;
    }

    /**
     * 校验监听地址非空白。
     *
     * @param host 监听地址
     * @return 校验后的监听地址
     * @throws IllegalArgumentException {@code host} 为 {@code null} 或空白
     */
    public static String requireHost(String host) {
        if (isBlank(host)) {
            throw new IllegalArgumentException("host can not be blank");
        }
        return host;
    }

    /**
     * 校验监听端口在 {@code [0, 65535]} 内；{@code 0} 表示由系统分配随机端口。
     *
     * @param port 监听端口
     * @return 校验后的监听端口
     * @throws IllegalArgumentException {@code port} 超出 {@code [0, 65535]}
     */
    public static int requirePort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in [0, 65535] but was " + port);
        }
        return port;
    }

    /**
     * 校验空闲会话回收阈值不小于 {@code 0}；{@code 0} 表示禁用。
     *
     * @param seconds 空闲会话回收阈值（秒）
     * @return 校验后的空闲会话回收阈值（秒）
     * @throws IllegalArgumentException {@code seconds} 小于 {@code 0}
     */
    public static int requireSessionIdleTimeoutSeconds(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("sessionIdleTimeoutSeconds must not be negative but was " + seconds);
        }
        return seconds;
    }

    /**
     * 校验挂起连接队列上限在 {@code [-1, 65535]} 内；{@code -1} 表示不设置。
     *
     * @param maxPendingConnections 挂起连接队列上限
     * @return 校验后的挂起连接队列上限
     * @throws IllegalArgumentException {@code maxPendingConnections} 超出 {@code [-1, 65535]}
     */
    public static int requireMaxPendingConnections(int maxPendingConnections) {
        if (maxPendingConnections < -1 || maxPendingConnections > 65535) {
            throw new IllegalArgumentException("maxPendingConnections must be in [-1, 65535] but was " + maxPendingConnections);
        }
        return maxPendingConnections;
    }

    /**
     * 校验关闭码在 {@code [1000, 4999]} 内。
     *
     * @param code 关闭码
     * @return 校验后的关闭码
     * @throws IllegalArgumentException {@code code} 超出 {@code [1000, 4999]}
     */
    public static int requireCloseCode(int code) {
        if (code < CLOSE_CODE_MIN || code > CLOSE_CODE_MAX) {
            throw new IllegalArgumentException("close code must be in [1000, 4999] but was " + code);
        }
        return code;
    }

    /**
     * 判断字符串是否为 {@code null} 或全空白。
     *
     * @param value 待判定字符串
     * @return 为 {@code null} 或全空白时 {@code true}
     */
    private static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
