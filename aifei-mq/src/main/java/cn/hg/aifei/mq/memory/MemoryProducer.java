/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MqValidator;
import cn.hg.aifei.mq.internal.TextCodec;

/**
 * 生产视图：把消息委托给所属总线投递，本身不持有订阅登记。
 */
final class MemoryProducer implements Producer {

    /**
     * 所属总线。
     */
    private final MemoryMessageBus bus;

    /**
     * 由总线创建。
     *
     * @param bus 所属总线
     */
    MemoryProducer(MemoryMessageBus bus) {
        this.bus = bus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SendResult send(Message message) {
        MqValidator.requireMessage(message);
        return bus.publish(message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SendResult send(String topic, byte[] payload) {
        return send(Message.of(topic, payload));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SendResult send(String topic, String text) {
        MqValidator.requireText(text);
        return send(Message.of(topic, TextCodec.encode(text)));
    }
}
