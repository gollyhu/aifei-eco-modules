/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.MessageListener;

/**
 * 消费者视图：校验参数后把订阅登记与取消委托给所属总线。
 *
 * <p>全部后端共用这一实现，后端只需实现 {@link MessageBus#subscribe(String, MessageListener)} 与
 * {@link MessageBus#unsubscribe(String, MessageListener)}。非承诺 API：仅供装配与后端实现使用，不属于
 * 公共契约面。</p>
 */
public final class BusConsumer implements Consumer {

    /**
     * 所属总线。
     */
    private final MessageBus bus;

    /**
     * 创建消费者视图。
     *
     * @param bus 所属总线
     * @throws IllegalArgumentException {@code bus} 为 {@code null}
     */
    public BusConsumer(MessageBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus can not be null");
        }
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
