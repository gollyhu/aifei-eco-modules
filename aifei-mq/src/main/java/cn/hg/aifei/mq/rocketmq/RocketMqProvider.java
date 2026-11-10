/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rocketmq;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * RocketMQ 后端 provider：无状态，按配置创建总线；{@link #TYPE} 是后端标识字面量的唯一定义点。
 */
public class RocketMqProvider implements MqProvider {

    public static final String TYPE = "rocketmq";

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     * @throws cn.hg.aifei.mq.MqException 未配置 nameserver 地址或生产者启动失败（nameserver 不可达属懒连接，
     *         不在构建期暴露）
     */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new RocketMqMessageBus(config);
    }
}
