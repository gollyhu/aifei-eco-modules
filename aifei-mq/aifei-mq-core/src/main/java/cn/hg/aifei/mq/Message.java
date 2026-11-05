/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqValidator;
import java.util.Arrays;

/**
 * 一次消息载荷：主题 + 字节内容，不可变；相等性按「主题 + 字节逐位」判定，{@code byte[]} 可变故出入参均做防御性拷贝。
 */
public final class Message {

    private final String topic;

    private final byte[] payload;

    private Message(String topic, byte[] payload) {
        this.topic = topic;
        this.payload = payload;
    }

    /**
     * @param topic 主题，非空白
     * @param payload 字节载荷，非 {@code null}；此处拷贝入参
     * @return 消息
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     */
    public static Message of(String topic, byte[] payload) {
        MqValidator.requireTopic(topic);
        MqValidator.requirePayload(payload);
        return new Message(topic, payload.clone());
    }

    /**
     * @return 主题
     */
    public String topic() {
        return topic;
    }

    /**
     * @return 字节载荷的副本：{@code byte[]} 可变，不拷贝会破坏不可变性
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
