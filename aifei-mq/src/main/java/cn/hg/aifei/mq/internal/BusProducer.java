/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import java.nio.charset.StandardCharsets;

/**
 * 生产者视图：校验参数后把消息委托给所属总线投递，本身不持有订阅登记。
 *
 * <p>全部后端共用这一实现，后端只需实现 {@link MessageBus#publish(Message)}。文本载荷的 UTF-8 编码规则
 * 也只在本类定义。非承诺 API：仅供装配与后端实现使用，不属于公共契约面。</p>
 */
public final class BusProducer implements Producer {

    /**
     * 所属总线。
     */
    private final MessageBus bus;

    /**
     * 创建生产者视图。
     *
     * @param bus 所属总线
     * @throws IllegalArgumentException {@code bus} 为 {@code null}
     */
    public BusProducer(MessageBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus can not be null");
        }
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
        // 文本载荷的 UTF-8 编码：全模块唯一定义点
        return send(Message.of(topic, text.getBytes(StandardCharsets.UTF_8)));
    }
}
