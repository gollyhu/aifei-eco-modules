/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.SendResult;

/**
 * 后端统一内部消息总线契约：一条总线同时承担投递与订阅登记，并承担生命周期。
 *
 * <p>面向应用的 {@code Producer} / {@code Consumer} 视图由 {@link BusProducer} / {@link BusConsumer} 在本
 * 总线之上薄封装，后端实现只需提供投递与登记。非承诺 API，仅供装配与实现使用。</p>
 */
public interface MessageBus extends AutoCloseable {

    /**
     * 投递一条消息。
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalStateException 总线已关闭
     * @throws cn.hg.aifei.mq.MqException 真实后端运行时故障（如 broker 不可达、发送未成功）时抛出；内存后端不抛
     */
    SendResult publish(Message message);

    /**
     * 订阅指定主题；此后该主题上的每条消息都会回调 {@code listener}。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws cn.hg.aifei.mq.MqException 真实后端订阅主题或启动消费者失败时抛出；内存后端不抛
     */
    void subscribe(String topic, MessageListener listener);

    /**
     * 取消订阅：移除该监听器实例；重复取消、取消从未登记的订阅均为幂等静默 no-op。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     */
    void unsubscribe(String topic, MessageListener listener);

    /**
     * 关闭总线；重复调用是幂等的。关闭后发送与订阅视为状态非法。
     */
    @Override
    void close();
}
