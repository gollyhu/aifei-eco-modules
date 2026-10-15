/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import cn.hg.aifei.websocket.internal.WebSocketValidator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端配置：目标地址、连接超时、自动重连参数与握手请求头。
 *
 * <p>心跳与自动重连默认关闭，需显式开启。心跳间隔见 {@link WebSocketConfig#heartbeatSeconds()}。</p>
 */
public final class ClientConfig extends WebSocketConfig {

    /**
     * 目标地址；必填。
     */
    private String uri;

    /**
     * 连接超时（毫秒）。
     */
    private int connectTimeoutMillis = 5000;

    /**
     * 是否开启自动重连。
     */
    private boolean autoReconnect = false;

    /**
     * 连续失败重试上限。
     */
    private int maxRetries = 5;

    /**
     * 首次重试延迟（毫秒）。
     */
    private long initialRetryDelayMillis = 500L;

    /**
     * 单次重试延迟上限（毫秒）。
     */
    private long maxRetryDelayMillis = 30000L;

    /**
     * 抖动比例，取值 {@code [0.0, 1.0]}。
     */
    private double retryJitterRatio = 0.2;

    /**
     * 握手附加请求头；默认空。
     */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * 返回目标地址。
     *
     * @return 目标地址；未设置时为 {@code null}
     */
    public String uri() {
        return uri;
    }

    /**
     * 设置目标地址。
     *
     * @param uri 目标地址，形如 {@code ws://host:port/path} 或 {@code wss://...}
     * @return 本配置对象
     * @throws IllegalArgumentException {@code uri} 为空白
     */
    public ClientConfig setUri(String uri) {
        this.uri = WebSocketValidator.requireUri(uri);
        return this;
    }

    /**
     * 返回连接超时（毫秒）。
     *
     * @return 连接超时（毫秒）
     */
    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * 设置连接超时（毫秒）。
     *
     * @param millis 连接超时（毫秒）
     * @return 本配置对象
     * @throws IllegalArgumentException {@code millis} 小于 {@code 1}
     */
    public ClientConfig setConnectTimeoutMillis(int millis) {
        this.connectTimeoutMillis = WebSocketValidator.requireConnectTimeoutMillis(millis);
        return this;
    }

    /**
     * 返回是否开启自动重连。
     *
     * @return 开启自动重连时 {@code true}
     */
    public boolean autoReconnect() {
        return autoReconnect;
    }

    /**
     * 设置是否开启自动重连。
     *
     * @param autoReconnect 是否开启自动重连
     * @return 本配置对象
     */
    public ClientConfig setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
        return this;
    }

    /**
     * 返回连续失败重试上限。
     *
     * @return 连续失败重试上限
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * 设置连续失败重试上限。
     *
     * @param maxRetries 连续失败重试上限
     * @return 本配置对象
     * @throws IllegalArgumentException {@code maxRetries} 超出 {@code [1, 2147483]}
     */
    public ClientConfig setMaxRetries(int maxRetries) {
        this.maxRetries = WebSocketValidator.requireMaxRetries(maxRetries);
        return this;
    }

    /**
     * 返回首次重试延迟（毫秒）。
     *
     * @return 首次重试延迟（毫秒）
     */
    public long initialRetryDelayMillis() {
        return initialRetryDelayMillis;
    }

    /**
     * 设置首次重试延迟（毫秒）。
     *
     * @param millis 首次重试延迟（毫秒）
     * @return 本配置对象
     * @throws IllegalArgumentException {@code millis} 小于 {@code 1}
     */
    public ClientConfig setInitialRetryDelayMillis(long millis) {
        this.initialRetryDelayMillis = WebSocketValidator.requireInitialRetryDelayMillis(millis);
        return this;
    }

    /**
     * 返回单次重试延迟上限（毫秒）。
     *
     * @return 单次重试延迟上限（毫秒）
     */
    public long maxRetryDelayMillis() {
        return maxRetryDelayMillis;
    }

    /**
     * 设置单次重试延迟上限（毫秒）。
     *
     * <p>与 {@link #initialRetryDelayMillis()} 的大小关系在构建实例时校验。</p>
     *
     * @param millis 单次重试延迟上限（毫秒）
     * @return 本配置对象
     */
    public ClientConfig setMaxRetryDelayMillis(long millis) {
        this.maxRetryDelayMillis = millis;
        return this;
    }

    /**
     * 返回重试抖动比例。
     *
     * @return 重试抖动比例
     */
    public double retryJitterRatio() {
        return retryJitterRatio;
    }

    /**
     * 设置重试抖动比例。
     *
     * @param ratio 重试抖动比例
     * @return 本配置对象
     * @throws IllegalArgumentException {@code ratio} 超出 {@code [0.0, 1.0]}
     */
    public ClientConfig setRetryJitterRatio(double ratio) {
        this.retryJitterRatio = WebSocketValidator.requireRetryJitterRatio(ratio);
        return this;
    }

    /**
     * 返回握手附加请求头。
     *
     * @return 请求头；不为 {@code null}
     */
    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * 设置握手附加请求头。
     *
     * @param headers 请求头
     * @return 本配置对象
     * @throws IllegalArgumentException {@code headers} 为 {@code null}
     */
    public ClientConfig setHeaders(Map<String, String> headers) {
        this.headers = WebSocketValidator.requireHeaders(headers);
        return this;
    }
}
