/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.kafka;

import cn.hg.aifei.mq.internal.AbstractMqProvider;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;

/**
 * Kafka 后端 provider：创建 Kafka 总线，后端标识为 {@link #TYPE}。
 *
 * <p>无实例字段，可被多线程复用；配置只在 {@link #buildBus(MqConfig)} 内经参数现读现用。后端专有键见
 * {@code KafkaMessageBus}。</p>
 */
public class KafkaProvider extends AbstractMqProvider {

    /**
     * 后端类型标识，也是后端标识字面量的唯一定义点。
     */
    public static final String TYPE = "kafka";

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @throws cn.hg.aifei.mq.MqException 未配置 broker 地址，或生产者客户端初始化失败——Kafka 客户端懒连接，
     *         构造期不校验 broker 连通性
     */
    @Override
    protected MessageBus buildBus(MqConfig config) {
        return new KafkaMessageBus(config);
    }
}
