/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.mqtt5;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MqValidator;
import cn.hg.aifei.mq.internal.TextCodec;

/**
 * 生产视图：校验参数后把消息委托给所属总线，经共用客户端发布。
 *
 * <p>发送成功仅表示消息已交给客户端发布，不代表已被消费。</p>
 */
final class Mqtt5Producer implements Producer {

    /**
     * 所属总线。
     */
    private final Mqtt5MessageBus bus;

    /**
     * 由总线创建。
     *
     * @param bus 所属总线
     */
    Mqtt5Producer(Mqtt5MessageBus bus) {
        this.bus = bus;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException 所属总线已关闭
     * @throws cn.hg.aifei.mq.MqException 发布失败（客户端 / 网络故障）
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
     * @throws cn.hg.aifei.mq.MqException 发布失败（客户端 / 网络故障）
     */
    @Override
    public SendResult send(String topic, byte[] payload) {
        return send(Message.of(topic, payload));
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException 所属总线已关闭
     * @throws cn.hg.aifei.mq.MqException 发布失败（客户端 / 网络故障）
     */
    @Override
    public SendResult send(String topic, String text) {
        MqValidator.requireText(text);
        return send(Message.of(topic, TextCodec.encode(text)));
    }
}
