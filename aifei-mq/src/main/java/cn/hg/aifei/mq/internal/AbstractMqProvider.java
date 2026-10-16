/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

/**
 * provider 的装配骨架：{@link #createBus(MqConfig)} 作为模板方法完成校验后委托子类构建总线。
 *
 * <p>基类不持有实例字段，可被多线程复用；总线由子类的 {@link #buildBus(MqConfig)} 创建。</p>
 */
public abstract class AbstractMqProvider implements MqProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String type();

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     * @throws cn.hg.aifei.mq.MqException 真实后端构建总线失败（如必填配置缺失、生产者客户端启动失败）时由
     *         {@link #buildBus(MqConfig)} 传播；构建总线<b>不校验</b>后端连通性（如 RocketMQ nameserver 不可达
     *         不在此失败，见 {@code RocketMqMessageBus}）；内存后端不抛
     */
    @Override
    public final MessageBus createBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        return buildBus(config);
    }

    /**
     * 创建本后端的总线；配置已通过非空校验。
     *
     * @param config 配置对象
     * @return 消息总线
     */
    protected abstract MessageBus buildBus(MqConfig config);
}
