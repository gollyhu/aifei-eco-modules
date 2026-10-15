/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import cn.hg.aifei.mq.internal.AbstractMqProvider;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MessageBus;

/**
 * 内存后端 provider：创建进程内发布-订阅总线，无额外配置键。
 */
public class MemoryMqProvider extends AbstractMqProvider {

    /**
     * 后端类型标识；引用配置域的默认类型，避免字面量重复。
     */
    public static final String TYPE = MqConfig.DEFAULT_TYPE;

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return TYPE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MessageBus buildBus(MqConfig config) {
        return new MemoryMessageBus(config);
    }
}
