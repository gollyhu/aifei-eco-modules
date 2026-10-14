/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

import cn.aifei.websocket.internal.WebSocketValidator;
import cn.aifei.websocket.spi.MessageCodec;

/**
 * 客户端与服务端的公共配置：传输后端类型、消息编解码器与心跳间隔。
 *
 * <p>配置对象是可变 POJO，但实现只在构建实例时读取一次；构建完成后再修改配置对象不影响已构建的实例。
 * 字段级校验在 setter 内完成，规则的定义点为 {@code internal.WebSocketValidator}；跨字段校验在构建时完成。</p>
 *
 * @see ClientConfig
 * @see ServerConfig
 */
public abstract class WebSocketConfig {

    /**
     * 传输后端名，默认 {@code javawebsocket}。
     */
    private String type = "javawebsocket";

    /**
     * 消息编解码器；{@code null} 表示不使用对象编解码。
     */
    private MessageCodec codec;

    /**
     * 心跳间隔（秒）；{@code 0} 表示禁用。
     */
    private int heartbeatSeconds = 0;

    /**
     * 返回传输后端名。
     *
     * @return 传输后端名；默认 {@code "javawebsocket"}
     */
    public String type() {
        return type;
    }

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
     * 设置传输后端名。
     *
     * @param type 传输后端名
     * @return 本配置对象
     * @throws IllegalArgumentException {@code type} 为空白
     */
    public WebSocketConfig setType(String type) {
        this.type = WebSocketValidator.requireType(type);
        return this;
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
