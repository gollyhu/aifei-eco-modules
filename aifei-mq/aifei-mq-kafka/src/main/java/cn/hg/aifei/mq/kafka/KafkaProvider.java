/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.kafka;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * Kafka 后端 provider：无状态，按配置创建总线；{@link #TYPE} 是后端标识字面量的唯一定义点，专有键见
 * {@code KafkaMessageBus}。
 */
public class KafkaProvider implements MqProvider {

    public static final String TYPE = "kafka";

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /** {@inheritDoc} 未配置 broker 地址或生产者初始化失败即抛 {@link cn.hg.aifei.mq.MqException}（客户端懒连接，
     * 构造期不校验 broker 连通性）。 */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new KafkaMessageBus(config);
    }
}
