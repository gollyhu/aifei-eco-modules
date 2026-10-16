/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Producer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行期实例注册表：按实例名登记装配好的生产者与消费者，供 {@code ProducerUtil} / {@code ConsumerUtil} 取用。
 *
 * <p>登记与清除发生在应用装配的启动 / 停止阶段；尚未登记过任何实例（或已清除）时取用抛
 * {@link IllegalStateException}，实例名非法或未登记抛 {@link IllegalArgumentException}——取用期一律
 * <b>先生命周期、后参数</b>。实例的持有只在本类定义。</p>
 */
public final class MqHolder {

    /**
     * 持有锁，登记、取用与清除共用。
     */
    private static final Object LOCK = new Object();

    /**
     * 实例名到 (Producer, Consumer) 的映射；保持登记顺序以便异常消息稳定。
     */
    private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

    /**
     * 禁止创建工具类实例。
     */
    private MqHolder() {
    }

    /**
     * 登记一个实例的生产者与消费者，覆盖同名的既有登记。
     *
     * @param instanceName 实例名；主实例传 {@link MqConfig#MAIN_INSTANCE_NAME}
     * @param producer 该实例的生产者
     * @param consumer 该实例的消费者
     * @throws IllegalArgumentException 实例名非法，或 {@code producer} / {@code consumer} 为 {@code null}
     */
    public static void register(String instanceName, Producer producer, Consumer consumer) {
        MqConfig.requireInstanceName(instanceName);
        MqValidator.requireProducer(producer);
        MqValidator.requireConsumer(consumer);
        synchronized (LOCK) {
            REGISTRY.put(instanceName, new Entry(producer, consumer));
        }
    }

    /**
     * 取指定实例的生产者。
     *
     * @param instanceName 实例名；主实例传 {@link MqConfig#MAIN_INSTANCE_NAME}
     * @return 已登记的生产者
     * @throws IllegalStateException 尚未登记过任何实例，或登记已被清除
     * @throws IllegalArgumentException 实例名非法或该实例未登记
     */
    public static Producer producer(String instanceName) {
        synchronized (LOCK) {
            return lookup(instanceName).producer;
        }
    }

    /**
     * 取指定实例的消费者。
     *
     * @param instanceName 实例名；主实例传 {@link MqConfig#MAIN_INSTANCE_NAME}
     * @return 已登记的消费者
     * @throws IllegalStateException 尚未登记过任何实例，或登记已被清除
     * @throws IllegalArgumentException 实例名非法或该实例未登记
     */
    public static Consumer consumer(String instanceName) {
        synchronized (LOCK) {
            return lookup(instanceName).consumer;
        }
    }

    /**
     * 清除全部实例的登记；清除后取用立即失败。
     */
    public static void clear() {
        synchronized (LOCK) {
            REGISTRY.clear();
        }
    }

    /**
     * 取用期判定，先生命周期后参数。
     */
    private static Entry lookup(String instanceName) {
        if (REGISTRY.isEmpty()) {
            throw new IllegalStateException(
                    "MqHolder has not been initialized. Add MqPlugin first."
            );
        }
        MqConfig.requireInstanceName(instanceName);
        Entry entry = REGISTRY.get(instanceName);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "unknown mq instance: " + instanceName
                            + ", registered instances: " + new ArrayList<>(REGISTRY.keySet())
            );
        }
        return entry;
    }

    /**
     * 单个实例的 (Producer, Consumer) 配对。
     */
    private static final class Entry {

        private final Producer producer;

        private final Consumer consumer;

        private Entry(Producer producer, Consumer consumer) {
            this.producer = producer;
            this.consumer = consumer;
        }
    }
}
