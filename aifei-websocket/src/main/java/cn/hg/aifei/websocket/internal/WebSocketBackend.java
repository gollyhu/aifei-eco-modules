/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

/**
 * 后端标识的唯一定义点。
 *
 * <p>传输实现唯一且内建于 {@code internal.jdk}，因此后端标识不是配置项：{@code WebSocketClient#backendType()}
 * 与 {@code WebSocketServer#backendType()} 固定返回 {@link #TYPE}，不读配置。本类属内部实现，不是公共 API。</p>
 */
public final class WebSocketBackend {

    /**
     * 内建传输实现的后端标识。
     */
    public static final String TYPE = "jdk";

    /**
     * 禁止创建工具类实例。
     */
    private WebSocketBackend() {
    }
}
