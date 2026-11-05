/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.mqtt;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * MQTT 后端 provider：无状态，按配置创建 MQTT 总线。
 */
public class MqttProvider implements MqProvider {

    /** 后端类型标识，也是后端标识字面量的唯一定义点。 */
    public static final String TYPE = "mqtt";

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /** {@inheritDoc} 未配置 broker 地址或构造期内建连失败即抛 {@link cn.hg.aifei.mq.MqException}（fail-fast，
     * 与 RocketMQ 的懒连接相反）。 */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new MqttMessageBus(config);
    }
}
