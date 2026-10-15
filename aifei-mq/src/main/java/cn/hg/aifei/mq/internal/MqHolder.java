/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Producer;

/**
 * 运行期单例持有：登记装配好的生产者与消费者，供 {@code MqKit} 取用。
 *
 * <p>登记与清除发生在应用装配的启动 / 停止阶段；未登记时取用抛 {@link IllegalStateException}。
 * 单例的持有只在本类定义。</p>
 */
public final class MqHolder {

    /**
     * 持有锁，登记、取用与清除共用。
     */
    private static final Object LOCK = new Object();

    /**
     * 已登记的生产者；{@code null} 表示尚未登记。
     */
    private static Producer producer;

    /**
     * 已登记的消费者；{@code null} 表示尚未登记。
     */
    private static Consumer consumer;

    /**
     * 禁止创建工具类实例。
     */
    private MqHolder() {
    }

    /**
     * 登记生产者与消费者，覆盖既有登记。
     *
     * @param producer 生产者
     * @param consumer 消费者
     * @throws IllegalArgumentException {@code producer} 或 {@code consumer} 为 {@code null}
     */
    public static void register(Producer producer, Consumer consumer) {
        MqValidator.requireProducer(producer);
        MqValidator.requireConsumer(consumer);
        synchronized (LOCK) {
            MqHolder.producer = producer;
            MqHolder.consumer = consumer;
        }
    }

    /**
     * 取生产者。
     *
     * @return 已登记的生产者
     * @throws IllegalStateException 尚未登记
     */
    public static Producer producer() {
        synchronized (LOCK) {
            if (producer == null) {
                throw new IllegalStateException(
                        "MqHolder has not been initialized. Add MqPlugin first."
                );
            }
            return producer;
        }
    }

    /**
     * 取消费者。
     *
     * @return 已登记的消费者
     * @throws IllegalStateException 尚未登记
     */
    public static Consumer consumer() {
        synchronized (LOCK) {
            if (consumer == null) {
                throw new IllegalStateException(
                        "MqHolder has not been initialized. Add MqPlugin first."
                );
            }
            return consumer;
        }
    }

    /**
     * 清除登记；清除后取用立即失败。
     */
    public static void clear() {
        synchronized (LOCK) {
            producer = null;
            consumer = null;
        }
    }
}
