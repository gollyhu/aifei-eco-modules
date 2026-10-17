/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.mqtt5;

import cn.hg.aifei.mq.internal.AbstractMqProvider;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;

/**
 * MQTT v5.0 后端 provider：创建 MQTT v5 总线，后端标识为 {@link #TYPE}。
 *
 * <p>与 {@code mqtt}（v3.1.1）各有独立的 provider、总线与配置命名空间，两域互不相通，也无 {@code version}
 * 之类的选择键。无实例字段，可被多线程复用；配置只在 {@link #buildBus(MqConfig)} 内经参数现读现用。后端专有键
 * 见 {@code Mqtt5MessageBus}。</p>
 */
public class Mqtt5Provider extends AbstractMqProvider {

    /**
     * 后端类型标识，也是后端标识字面量的唯一定义点。
     */
    public static final String TYPE = "mqtt5";

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @throws cn.hg.aifei.mq.MqException 未配置 broker 地址，或构造期内建连失败（broker 不可达）——MQTT v5
     *         后端在启动期即暴露连通性故障，与 RocketMQ 的懒连接相反
     */
    @Override
    protected MessageBus buildBus(MqConfig config) {
        return new Mqtt5MessageBus(config);
    }
}
