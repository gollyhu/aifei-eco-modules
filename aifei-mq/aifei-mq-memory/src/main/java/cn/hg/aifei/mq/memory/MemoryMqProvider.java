/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * 内存后端 provider：无状态、无配置键，创建进程内发布-订阅总线。
 */
public class MemoryMqProvider implements MqProvider {

    /** 后端类型标识；引用配置域的默认类型，避免字面量重复。 */
    public static final String TYPE = MqConfig.DEFAULT_TYPE;

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /** {@inheritDoc} {@code config} 为 {@code null} 时抛 {@link IllegalArgumentException}。 */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new MemoryMessageBus(config);
    }
}
