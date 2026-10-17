/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.redisstream;

import cn.hg.aifei.mq.MessageBusContractTest;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import java.util.Properties;
import java.util.UUID;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;

/**
 * Redis Streams 后端继承运行 {@link MessageBusContractTest} 的<b>共同底线</b>契约。
 *
 * <p><b>默认关闭</b>：未开启时整类经 {@code Assume} 跳过；用 {@code -Dredisstream.integration=true} 开启，
 * 并可用 {@code -Dredisstream.host=host} / {@code -Dredisstream.port=port} 指定 Redis
 * （默认 {@code 127.0.0.1} / {@code 6379}）。开启后不得以 {@code Assume} 掩盖后端故障：Jedis 连接池
 * <b>懒建连</b>，构造（{@link #newBus()}）通常不报错，故障延迟到首次收发处以 {@code MqException} 使用例
 * 失败——不得把「建池未报错」当作「Redis 可达」（与 RocketMQ / Kafka 同构）。</p>
 *
 * <p>隔离：每个用例各自新建总线，故各持有<b>随机消费组 {@code group}</b>、<b>随机 {@code consumer-name}</b>
 * 与 {@link #topic(String)} 派生的<b>随机流键前缀</b>，避免与历史残留消息、位点、并行实例互相干扰；
 * {@link #closeBus()} 兜底关闭总线（基类用例内已关闭，关闭幂等）。</p>
 *
 * <p>本类<b>只</b>继承运行共同底线用例，不写任何后端差异断言（投递模型、有序性、投递保证、
 * {@code messageId} 语义、消费位点 / 消费组、取消订阅的尽力语义等差异见
 * {@code docs/design.md} §5.6 (B) / (C)）。</p>
 */
public class RedisStreamMessageBusContractTest extends MessageBusContractTest {

    /**
     * 集成测试开关；未开启时整类跳过。
     */
    private static final boolean ENABLED = Boolean.getBoolean("redisstream.integration");

    /**
     * Redis 主机。
     */
    private static final String HOST = System.getProperty("redisstream.host", "127.0.0.1");

    /**
     * Redis 端口。
     */
    private static final String PORT = System.getProperty("redisstream.port", "6379");

    /**
     * 等待上限：真实后端含 Redis 往返、拉取循环线程调度与消费组创建，故放宽到 60 秒。
     */
    private static final long AWAIT_TIMEOUT_MILLIS = 60_000L;

    /**
     * 流键随机前缀的固定前缀，便于识别本模块在 Redis 上创建的流。
     */
    private static final String TOPIC_PREFIX = "aifeimq_";

    /**
     * 消费组、消费者名与流键前缀共用的随机后缀；每个用例一个新实例，故互不相同。
     */
    private final String instanceId = UUID.randomUUID().toString().replace("-", "");

    /**
     * 最近创建的总线，供 {@link #closeBus()} 兜底关闭。
     */
    private MessageBus bus;

    /**
     * 未开启集成测试时跳过整类；开启后不提供任何「连不上即通过」的退路。
     */
    @Before
    public void requireIntegrationEnabled() {
        Assume.assumeTrue("Redis Streams integration tests: enable -Dredisstream.integration=true", ENABLED);
    }

    /**
     * 关闭本用例创建的总线；重复关闭是幂等的。
     */
    @After
    public void closeBus() {
        if (bus != null) {
            bus.close();
            bus = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected long awaitTimeoutMillis() {
        return AWAIT_TIMEOUT_MILLIS;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String topic(String name) {
        return TOPIC_PREFIX + instanceId + "_" + name;
    }

    /**
     * 以随机消费组 / 消费者名与指定 Redis 地址创建 Redis Streams 总线；连接池懒建连，构造不校验 Redis
     * 连通性，不可达通常延迟到首次收发才失败。
     *
     * @return 新的 Redis Streams 总线
     */
    @Override
    protected MessageBus newBus() {
        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, RedisStreamProvider.TYPE);
        // 键名与实现包内的后端专有键一致：host / port / group / consumer-name
        properties.setProperty("mq.redisstream.host", HOST);
        properties.setProperty("mq.redisstream.port", PORT);
        properties.setProperty("mq.redisstream.group", "aifeimq_group_" + instanceId);
        properties.setProperty("mq.redisstream.consumer-name", "aifeimq_consumer_" + instanceId);

        MessageBus created = new RedisStreamMessageBus(MqConfig.parse(properties,
                new MqProviderRegistry().types(), MqConfig.MAIN_INSTANCE_NAME));
        this.bus = created;
        return created;
    }
}
