/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqValidator;

/**
 * 一次发送的结果：主题 + 消息标识，不可变；投递失败即抛异常，故不设成功标志。{@code messageId} 仅供诊断，唯一性仅本进程。
 */
public final class SendResult {

    private final String topic;

    private final String messageId;

    private SendResult(String topic, String messageId) {
        this.topic = topic;
        this.messageId = messageId;
    }

    /**
     * @param topic 主题
     * @param messageId 消息标识，由后端或适配器生成且恒非空
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 或 {@code messageId} 空白
     */
    public static SendResult of(String topic, String messageId) {
        MqValidator.requireTopic(topic);
        MqValidator.requireMessageId(messageId);
        return new SendResult(topic, messageId);
    }

    /**
     * @return 主题
     */
    public String topic() {
        return topic;
    }

    /**
     * @return 消息标识
     */
    public String messageId() {
        return messageId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SendResult)) {
            return false;
        }
        SendResult other = (SendResult) obj;
        return this.topic.equals(other.topic) && this.messageId.equals(other.messageId);
    }

    @Override
    public int hashCode() {
        return 31 * topic.hashCode() + messageId.hashCode();
    }

    @Override
    public String toString() {
        return "SendResult{topic=" + topic + ", messageId=" + messageId + "}";
    }
}
