/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.mqtt5;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * MQTT v5.0 后端 provider：无状态，创建总线；与 {@code mqtt} 各有独立命名空间、无 {@code version} 选择键，
 * {@link #TYPE} 是后端标识字面量的唯一定义点，专有键见 {@code Mqtt5MessageBus}。
 */
public class Mqtt5Provider implements MqProvider {

    public static final String TYPE = "mqtt5";

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /** {@inheritDoc} 未配置 broker 地址或构造期内建连失败即抛 {@link cn.hg.aifei.mq.MqException}（fail-fast，
     * 与 RocketMQ 的懒连接相反）。 */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new Mqtt5MessageBus(config);
    }
}
