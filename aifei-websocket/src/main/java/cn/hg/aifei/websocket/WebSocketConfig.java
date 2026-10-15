/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import cn.hg.aifei.websocket.internal.WebSocketValidator;

/**
 * 客户端与服务端的公共配置：消息编解码器与心跳间隔。
 *
 * <p>传输实现唯一且内建于 {@code internal.jdk}，因此本类<b>没有后端类型字段</b>：{@code WebSocketClient#backendType()}
 * 与 {@code WebSocketServer#backendType()} 固定返回内建实现的自述值，不读配置。</p>
 *
 * <p>配置对象是可变 POJO，但实现只在构建实例时读取一次；构建完成后再修改配置对象不影响已构建的实例。
 * 字段级校验在 setter 内完成，规则的定义点为 {@code internal.WebSocketValidator}；跨字段校验在构建时完成。</p>
 *
 * @see ClientConfig
 * @see ServerConfig
 */
public abstract class WebSocketConfig {

    /**
     * 消息编解码器；{@code null} 表示不使用对象编解码。
     */
    private MessageCodec codec;

    /**
     * 心跳间隔（秒）；{@code 0} 表示禁用。
     */
    private int heartbeatSeconds = 0;

    /**
     * 返回消息编解码器。
     *
     * @return 消息编解码器；未配置时为 {@code null}
     */
    public MessageCodec codec() {
        return codec;
    }

    /**
     * 返回心跳间隔（秒）。
     *
     * @return 心跳间隔（秒）；{@code 0} 表示禁用
     */
    public int heartbeatSeconds() {
        return heartbeatSeconds;
    }

    /**
     * 设置消息编解码器。
     *
     * @param codec 消息编解码器；可为 {@code null}，表示不使用对象编解码
     * @return 本配置对象
     */
    public WebSocketConfig setCodec(MessageCodec codec) {
        this.codec = codec;
        return this;
    }

    /**
     * 设置心跳间隔（秒）。
     *
     * @param seconds 心跳间隔（秒）；{@code 0} 表示禁用
     * @return 本配置对象
     * @throws IllegalArgumentException {@code seconds} 超出 {@code [0, 2147483]}
     */
    public WebSocketConfig setHeartbeatSeconds(int seconds) {
        this.heartbeatSeconds = WebSocketValidator.requireHeartbeatSeconds(seconds);
        return this;
    }
}
