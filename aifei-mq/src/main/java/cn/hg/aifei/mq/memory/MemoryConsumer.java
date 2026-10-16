/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.internal.MqValidator;

/**
 * 消费视图：校验参数后把订阅登记与取消委托给所属总线。
 */
final class MemoryConsumer implements Consumer {

    /**
     * 所属总线。
     */
    private final MemoryMessageBus bus;

    /**
     * 由总线创建。
     *
     * @param bus 所属总线
     */
    MemoryConsumer(MemoryMessageBus bus) {
        this.bus = bus;
    }

    /**
     * {@inheritDoc}
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
