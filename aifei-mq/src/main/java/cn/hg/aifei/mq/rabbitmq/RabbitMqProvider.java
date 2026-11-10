/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rabbitmq;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * RabbitMQ 后端 provider：无状态，按配置创建总线；{@link #TYPE} 是后端标识字面量的唯一定义点，专有键见
 * {@code RabbitMqMessageBus}。
 */
public class RabbitMqProvider implements MqProvider {

    public static final String TYPE = "rabbitmq";

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /** {@inheritDoc} 未配置 broker 主机或构造期内建连失败即抛 {@link cn.hg.aifei.mq.MqException}（broker 不可达
     * 在启动期即暴露）。 */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new RabbitMqMessageBus(config);
    }
}
