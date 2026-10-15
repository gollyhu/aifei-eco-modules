/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqValidator;

/**
 * 一次发送的结果：主题 + 消息标识，不可变。
 *
 * <p>字段只有主题与消息标识，没有成功标志：投递失败即抛异常，成功才返回本对象。</p>
 */
public final class SendResult {

    /**
     * 主题。
     */
    private final String topic;

    /**
     * 消息标识，由后端生成。
     */
    private final String messageId;

    /**
     * 禁止外部构造，只能经 {@link #of(String, String)} 创建。
     */
    private SendResult(String topic, String messageId) {
        this.topic = topic;
        this.messageId = messageId;
    }

    /**
     * 创建一条发送结果。
     *
     * @param topic 主题
     * @param messageId 消息标识
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 或 {@code messageId} 空白
     */
    public static SendResult of(String topic, String messageId) {
        MqValidator.requireTopic(topic);
        MqValidator.requireMessageId(messageId);
        return new SendResult(topic, messageId);
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
     * 返回消息标识。
     *
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
