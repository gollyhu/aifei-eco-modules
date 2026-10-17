/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.redisstream;

import cn.hg.aifei.mq.internal.AbstractMqProvider;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;

/**
 * Redis Streams 后端 provider：创建 Redis Streams 总线，后端标识为 {@link #TYPE}。
 *
 * <p>无实例字段，可被多线程复用；配置只在 {@link #buildBus(MqConfig)} 内经参数现读现用。后端专有键见
 * {@code RedisStreamMessageBus}。</p>
 */
public class RedisStreamProvider extends AbstractMqProvider {

    /**
     * 后端类型标识，也是后端标识字面量的唯一定义点。
     */
    public static final String TYPE = "redisstream";

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
     * @throws cn.hg.aifei.mq.MqException 未配置 Redis 主机——Jedis 连接池懒建连，构造期不校验 Redis 连通性
     */
    @Override
    protected MessageBus buildBus(MqConfig config) {
        return new RedisStreamMessageBus(config);
    }
}
