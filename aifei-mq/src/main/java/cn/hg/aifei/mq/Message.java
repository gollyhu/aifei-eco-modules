/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqValidator;
import java.util.Arrays;

/**
 * 一次消息载荷：主题 + 字节内容，不可变。
 *
 * <p>主题要求非空白，字节内容要求非 {@code null}；字节内容的入参与出参都做防御性拷贝。相等性按
 * 「主题 + 字节内容」判定：主题相同且字节逐位相同才算相等。</p>
 */
public final class Message {

    /**
     * 主题。
     */
    private final String topic;

    /**
     * 字节载荷。
     */
    private final byte[] payload;

    /**
     * 禁止外部构造，只能经 {@link #of(String, byte[])} 创建；入参已通过校验并完成拷贝。
     */
    private Message(String topic, byte[] payload) {
        this.topic = topic;
        this.payload = payload;
    }

    /**
     * 创建一条消息；对字节载荷做防御性拷贝。
     *
     * @param topic 主题
     * @param payload 字节载荷
     * @return 消息
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     */
    public static Message of(String topic, byte[] payload) {
        MqValidator.requireTopic(topic);
        MqValidator.requirePayload(payload);
        return new Message(topic, payload.clone());
    }

    /**
     * 返回主题。
     *
     * @return 主题
     */
    public String topic() {
        return topic;
    }

    /**
     * 返回字节载荷的副本。
     *
     * @return 字节载荷副本
     */
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Message)) {
            return false;
        }
        Message other = (Message) obj;
        return this.topic.equals(other.topic) && Arrays.equals(this.payload, other.payload);
    }

    @Override
    public int hashCode() {
        return 31 * topic.hashCode() + Arrays.hashCode(payload);
    }

    @Override
    public String toString() {
        return "Message{topic=" + topic + ", payload=" + payload.length + " bytes}";
    }
}
