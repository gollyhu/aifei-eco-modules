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
     */
    public static SendResult send(Message message) {
        return producer().send(message);
    }

    /**
     * 发送字节载荷；规则同 {@link Producer#send(String, byte[])}。
     */
    public static SendResult send(String topic, byte[] payload) {
        return producer().send(topic, payload);
    }

    /**
     * 发送文本载荷；规则同 {@link Producer#send(String, String)}。
     */
    public static SendResult send(String topic, String text) {
        return producer().send(topic, text);
    }

    /**
     * 订阅主题；规则同 {@link Consumer#subscribe(String, MessageListener)}。
     */
    public static void subscribe(String topic, MessageListener listener) {
        consumer().subscribe(topic, listener);
    }
}
