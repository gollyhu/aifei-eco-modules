/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rabbitmq;

import cn.hg.aifei.mq.internal.AbstractMqProvider;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;

/**
 * RabbitMQ 后端 provider：创建 RabbitMQ 总线，后端标识为 {@link #TYPE}。
 *
 * <p>无实例字段，可被多线程复用；配置只在 {@link #buildBus(MqConfig)} 内经参数现读现用。后端专有键见
 * {@code RabbitMqMessageBus}。</p>
 */
public class RabbitMqProvider extends AbstractMqProvider {

    /**
     * 后端类型标识，也是后端标识字面量的唯一定义点。
     */
    public static final String TYPE = "rabbitmq";

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
     * @throws cn.hg.aifei.mq.MqException 未配置 broker 主机，或构造期内建连失败（broker 不可达）——RabbitMQ
     *         后端在启动期即暴露连通性故障
     */
    @Override
    protected MessageBus buildBus(MqConfig config) {
        return new RabbitMqMessageBus(config);
    }
}
