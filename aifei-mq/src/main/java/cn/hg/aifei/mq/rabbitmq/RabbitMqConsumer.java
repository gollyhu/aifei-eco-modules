/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rabbitmq;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.internal.MqValidator;

/**
 * 消费视图：校验参数后把订阅登记与取消委托给所属总线。
 */
final class RabbitMqConsumer implements Consumer {

    /**
     * 所属总线。
     */
    private final RabbitMqMessageBus bus;

    /**
     * 由总线创建。
     *
     * @param bus 所属总线
     */
    RabbitMqConsumer(RabbitMqMessageBus bus) {
        this.bus = bus;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException 所属总线已关闭
     * @throws cn.hg.aifei.mq.MqException 声明队列 / 交换机 / 绑定或开始投递失败（客户端 / 网络故障）
     */
    @Override
    public void subscribe(String topic, MessageListener listener) {
        MqValidator.requireTopic(topic);
        MqValidator.requireListener(listener);
        bus.subscribe(topic, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribe(String topic, MessageListener listener) {
        MqValidator.requireTopic(topic);
        MqValidator.requireListener(listener);
        bus.unsubscribe(topic, listener);
    }
}
