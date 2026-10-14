/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.spi;

import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.WebSocketCodecException;

/**
 * 编解码扩展点：业务对象与 {@link Message} 之间的相互转换，由消费方实现并通过
 * {@code ClientConfig#setCodec} / {@code ServerConfig#setCodec} 注入。
 *
 * <p>本模块不内置任何编解码实现，契约签名也不含第三方类型，因此实现类可以基于任意库。实现必须线程安全：
 * 同一编解码器会被多个连接与多个线程并发调用。</p>
 */
public interface MessageCodec {

    /**
     * 把业务对象编码为线缆消息；文本或二进制由实现决定。
     *
     * @param payload 业务对象
     * @return 编码后的消息
     * @throws IllegalArgumentException {@code payload} 为 {@code null}
     * @throws WebSocketCodecException 编码失败
     */
    Message encode(Object payload);

    /**
     * 把线缆消息解码为业务对象。
     *
     * @param message 线缆消息
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 解码后的业务对象
     * @throws IllegalArgumentException {@code message} 或 {@code type} 为 {@code null}
     * @throws WebSocketCodecException 解码失败
     */
    <T> T decode(Message message, Class<T> type);
}
