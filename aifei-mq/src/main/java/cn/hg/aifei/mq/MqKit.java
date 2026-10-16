/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqHolder;

/**
 * 发送与订阅的静态门面：不注入也能发消息、订阅主题。
 *
 * <p>{@link #producer()} / {@link #consumer()} 取应用启动时装配好的运行期实例；镜像方法只做转发，
 * 不重复校验、不转换异常，参数与异常契约同 {@link Producer} / {@link Consumer} 的对应方法。因此它们都
 * 要先取实例，可能在应用尚未启动或已经停止时抛 {@link IllegalStateException}。</p>
 */
public final class MqKit {

    private MqKit() {
    }

    /**
     * 取生产者。
     *
     * @return 生产者
     * @throws IllegalStateException 应用尚未启动或已经停止
     */
    public static Producer producer() {
        return MqHolder.producer();
    }

    /**
     * 取消费者。
     *
     * @return 消费者
     * @throws IllegalStateException 应用尚未启动或已经停止
     */
    public static Consumer consumer() {
        return MqHolder.consumer();
    }

    /**
     * 发送一条已构造的消息；规则同 {@link Producer#send(Message)}。
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    public static SendResult send(Message message) {
        return producer().send(message);
    }

    /**
     * 发送字节载荷；规则同 {@link Producer#send(String, byte[])}。
     *
     * @param topic 主题
     * @param payload 字节载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    public static SendResult send(String topic, byte[] payload) {
        return producer().send(topic, payload);
    }

    /**
     * 发送文本载荷；规则同 {@link Producer#send(String, String)}。
     *
     * @param topic 主题
     * @param text 文本载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code text} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    public static SendResult send(String topic, String text) {
        return producer().send(topic, text);
    }

    /**
     * 订阅主题；规则同 {@link Consumer#subscribe(String, MessageListener)}。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 空白或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或订阅时所属总线已停止
     * @throws MqException 真实后端订阅主题或启动消费者失败时抛出；内存后端不抛
     */
    public static void subscribe(String topic, MessageListener listener) {
        consumer().subscribe(topic, listener);
    }
}
