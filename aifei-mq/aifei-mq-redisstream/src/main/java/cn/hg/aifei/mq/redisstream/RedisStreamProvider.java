/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.redisstream;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProvider;

/**
 * Redis Streams 后端 provider：无状态，按配置创建总线；{@link #TYPE} 是后端标识字面量的唯一定义点，专有键见
 * {@code RedisStreamMessageBus}。
 */
public class RedisStreamProvider implements MqProvider {

    public static final String TYPE = "redisstream";

    /** {@inheritDoc} */
    @Override
    public String type() {
        return TYPE;
    }

    /** {@inheritDoc} 未配置 Redis 主机即抛 {@link cn.hg.aifei.mq.MqException}；连接池懒建连，构造期不校验连通性。 */
    @Override
    public MessageBus createBus(MqConfig config) {
        return new RedisStreamMessageBus(config);
    }
}
