/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketCodecException;
import cn.hg.aifei.websocket.spi.MessageCodec;

/**
 * 对象编解码的唯一定义点：客户端 {@code send(Object)} 与服务端 {@code broadcast(Object)} 都经此把业务对象
 * 编码为线缆消息，避免各骨架各自复制一份等价的编码逻辑。
 *
 * <p>校验与异常语义集中于此：{@code payload} 为 {@code null} 抛用户错误 {@link IllegalArgumentException}；
 * 未配置编解码器抛用户错误 {@link IllegalStateException}；编解码器抛出的任何运行时异常与返回
 * {@code null} 都按编码失败包装为运行态故障 {@link WebSocketCodecException}。包装后的异常消息不含任何
 * 第三方类型名。</p>
 */
public final class MessageCodecs {

    /**
     * 禁止创建工具类实例。
     */
    private MessageCodecs() {
    }

    /**
     * 用编解码器把业务对象编码为线缆消息。
     *
     * @param codec 编解码器；未配置时为 {@code null}
     * @param payload 业务对象
     * @return 编码后的消息；不为 {@code null}
     * @throws IllegalArgumentException {@code payload} 为 {@code null}
     * @throws IllegalStateException {@code codec} 为 {@code null}（未配置编解码器）
     * @throws WebSocketCodecException 编码失败，或编解码器返回 {@code null}
     */
    public static Message encode(MessageCodec codec, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload can not be null");
        }
        if (codec == null) {
            throw new IllegalStateException("a MessageCodec must be configured but none is configured");
        }
        Message message;
        try {
            message = codec.encode(payload);
        } catch (WebSocketCodecException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WebSocketCodecException("failed to encode payload", e);
        }
        if (message == null) {
            throw new WebSocketCodecException("codec returned null message");
        }
        return message;
    }
}
