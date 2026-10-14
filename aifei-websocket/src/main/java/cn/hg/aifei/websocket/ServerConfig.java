/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import cn.hg.aifei.websocket.internal.WebSocketValidator;

/**
 * 服务端配置：监听地址与端口、空闲会话回收、以及连接参数。
 *
 * <p>心跳间隔见 {@link WebSocketConfig#heartbeatSeconds()}；空闲会话回收与心跳相互独立，可同时启用。</p>
 */
public final class ServerConfig extends WebSocketConfig {

    /**
     * 监听地址。
     */
    private String host = "0.0.0.0";

    /**
     * 监听端口；{@code 0} 表示由系统分配随机端口。
     */
    private int port;

    /**
     * 是否允许地址复用。
     */
    private boolean reuseAddress = true;

    /**
     * 空闲会话回收阈值（秒）；{@code 0} 表示禁用。
     */
    private int sessionIdleTimeoutSeconds = 0;

    /**
     * 挂起连接队列上限；{@code -1} 表示不设置。
     */
    private int maxPendingConnections = -1;

    /**
     * 返回监听地址。
     *
     * @return 监听地址；默认 {@code "0.0.0.0"}
     */
    public String host() {
        return host;
    }

    /**
     * 设置监听地址。
     *
     * @param host 监听地址
     * @return 本配置对象
     * @throws IllegalArgumentException {@code host} 为空白
     */
    public ServerConfig setHost(String host) {
        this.host = WebSocketValidator.requireHost(host);
        return this;
    }

    /**
     * 返回监听端口。
     *
     * @return 监听端口；{@code 0} 表示由系统分配随机端口
     */
    public int port() {
        return port;
    }

    /**
     * 设置监听端口。
     *
     * @param port 监听端口；{@code 0} 表示由系统分配随机端口
     * @return 本配置对象
     * @throws IllegalArgumentException {@code port} 超出 {@code [0, 65535]}
     */
    public ServerConfig setPort(int port) {
        this.port = WebSocketValidator.requirePort(port);
        return this;
    }

    /**
     * 返回是否允许地址复用。
     *
     * @return 允许地址复用时 {@code true}
     */
    public boolean reuseAddress() {
        return reuseAddress;
    }

    /**
     * 设置是否允许地址复用。
     *
     * @param reuseAddress 是否允许地址复用
     * @return 本配置对象
     */
    public ServerConfig setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        return this;
    }

    /**
     * 返回空闲会话回收阈值（秒）。
     *
     * @return 空闲会话回收阈值（秒）；{@code 0} 表示禁用
     */
    public int sessionIdleTimeoutSeconds() {
        return sessionIdleTimeoutSeconds;
    }

    /**
     * 设置空闲会话回收阈值（秒）。
     *
     * @param seconds 空闲会话回收阈值（秒）；{@code 0} 表示禁用
     * @return 本配置对象
     * @throws IllegalArgumentException {@code seconds} 小于 {@code 0}
     */
    public ServerConfig setSessionIdleTimeoutSeconds(int seconds) {
        this.sessionIdleTimeoutSeconds = WebSocketValidator.requireSessionIdleTimeoutSeconds(seconds);
        return this;
    }

    /**
     * 返回挂起连接队列上限。
     *
     * @return 挂起连接队列上限；{@code -1} 表示不设置
     */
    public int maxPendingConnections() {
        return maxPendingConnections;
    }

    /**
     * 设置挂起连接队列上限。
     *
     * @param maxPendingConnections 挂起连接队列上限；{@code -1} 表示不设置
     * @return 本配置对象
     * @throws IllegalArgumentException {@code maxPendingConnections} 超出 {@code [-1, 65535]}
     */
    public ServerConfig setMaxPendingConnections(int maxPendingConnections) {
        this.maxPendingConnections = WebSocketValidator.requireMaxPendingConnections(maxPendingConnections);
        return this;
    }
}
