/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.Producer;

/**
 * 消息接口参数的共用校验：主题、载荷、文本、消息、消息标识、监听器、生产者与消费者。
 *
 * <p>校验规则与异常类型只在本类定义，实现类不得复制。违规一律抛 {@link IllegalArgumentException}。</p>
 */
public final class MqValidator {

    /**
     * 禁止创建工具类实例。
     */
    private MqValidator() {
    }

    /**
     * 校验主题非空白。
     *
     * @param topic 主题
     * @throws IllegalArgumentException {@code topic} 为 {@code null} 或只含空白
     */
    public static void requireTopic(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            throw new IllegalArgumentException("topic can not be blank");
        }
    }

    /**
     * 校验字节载荷非 {@code null}。
     *
     * @param payload 字节载荷
     * @throws IllegalArgumentException {@code payload} 为 {@code null}
     */
    public static void requirePayload(byte[] payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload can not be null");
        }
    }

    /**
     * 校验文本载荷非 {@code null}。
     *
     * @param text 文本载荷
     * @throws IllegalArgumentException {@code text} 为 {@code null}
     */
    public static void requireText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
    }

    /**
     * 校验消息非 {@code null}。
     *
     * @param message 消息
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     */
    public static void requireMessage(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("message can not be null");
        }
    }

    /**
     * 校验消息标识非空白。
     *
     * @param messageId 消息标识
     * @throws IllegalArgumentException {@code messageId} 为 {@code null} 或只含空白
     */
    public static void requireMessageId(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new IllegalArgumentException("messageId can not be blank");
        }
    }

    /**
     * 校验监听器非 {@code null}。
     *
     * @param listener 监听器
     * @throws IllegalArgumentException {@code listener} 为 {@code null}
     */
    public static void requireListener(MessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
    }

    /**
     * 校验生产者非 {@code null}。
     *
     * @param producer 生产者
     * @throws IllegalArgumentException {@code producer} 为 {@code null}
     */
    public static void requireProducer(Producer producer) {
        if (producer == null) {
            throw new IllegalArgumentException("producer can not be null");
        }
    }

    /**
     * 校验消费者非 {@code null}。
     *
     * @param consumer 消费者
     * @throws IllegalArgumentException {@code consumer} 为 {@code null}
     */
    public static void requireConsumer(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer can not be null");
        }
    }
}
