/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rocketmq;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MqValidator;
import cn.hg.aifei.mq.internal.TextCodec;

/**
 * 生产视图：校验参数后把消息委托给所属总线同步发送到 broker。
 *
 * <p>发送成功仅表示消息已提交到 broker，不代表已被消费。</p>
 */
final class RocketMqProducer implements Producer {

    /**
     * 所属总线。
     */
    private final RocketMqMessageBus bus;

    /**
     * 由总线创建。
     *
     * @param bus 所属总线
     */
    RocketMqProducer(RocketMqMessageBus bus) {
        this.bus = bus;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException 所属总线已关闭
     * @throws cn.hg.aifei.mq.MqException 发送失败：非 {@code SEND_OK}、broker / nameserver 不可达等后端运行时故障
     */
    @Override
    public SendResult send(Message message) {
        MqValidator.requireMessage(message);
        return bus.publish(message);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException 所属总线已关闭
     * @throws cn.hg.aifei.mq.MqException 发送失败（后端运行时故障）
     */
    @Override
    public SendResult send(String topic, byte[] payload) {
        return send(Message.of(topic, payload));
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException 所属总线已关闭
     * @throws cn.hg.aifei.mq.MqException 发送失败（后端运行时故障）
     */
    @Override
    public SendResult send(String topic, String text) {
        MqValidator.requireText(text);
        return send(Message.of(topic, TextCodec.encode(text)));
    }
}
