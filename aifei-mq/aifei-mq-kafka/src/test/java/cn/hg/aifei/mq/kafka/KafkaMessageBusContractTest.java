/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.kafka;

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
 * Kafka 后端继承运行 {@link MessageBusContractTest} 的<b>共同底线</b>契约。
 *
 * <p><b>默认关闭</b>：未开启时整类经 {@code Assume} 跳过；用 {@code -Dkafka.integration=true} 开启，
 * 并可用 {@code -Dkafka.bootstrap-servers=host:port} 指定 broker（默认 {@code 127.0.0.1:9092}）。
 * 开启后不得以 {@code Assume} 掩盖后端故障：Kafka 客户端自身<b>懒连接</b>，构造（{@link #newBus()}）通常
 * 不报错，故障延迟到首个 {@code send} / {@code subscribe} 处以 {@code MqException} 使用例失败——不得把
 * 「建总线未报错」当作「后端可达」（与 RocketMQ 同构）。</p>
 *
 * <p>隔离：每个用例各自新建总线，故各持有<b>随机消费组 {@code group-id}</b>、<b>随机 {@code client-id}</b>
 * 与 {@link #topic(String)} 派生的<b>随机主题前缀</b>，避免与历史残留消息、位点、并行实例互相干扰；
 * {@link #closeBus()} 兜底关闭总线（基类用例内已关闭，关闭幂等）。</p>
 *
 * <p>本类<b>只</b>继承运行共同底线用例，不写任何后端差异断言（投递模型、有序性、投递保证、
 * {@code messageId} 语义、消费位点 / 消费组、取消订阅的尽力语义等差异见
 * {@code docs/design.md} §5.6 (B) / (C)）。</p>
 */
public class KafkaMessageBusContractTest extends MessageBusContractTest {

    /**
     * 集成测试开关；未开启时整类跳过。
     */
    private static final boolean ENABLED = Boolean.getBoolean("kafka.integration");

    /**
     * broker 地址列表，形如 {@code host:port}，多个以 {@code ,} 分隔。
     */
    private static final String BOOTSTRAP_SERVERS =
            System.getProperty("kafka.bootstrap-servers", "127.0.0.1:9092");

    /**
     * 等待上限：真实后端含 broker 往返、消费线程调度与 rebalance，故放宽到 60 秒。
     */
    private static final long AWAIT_TIMEOUT_MILLIS = 60_000L;

    /**
     * 主题随机前缀的固定前缀，便于识别本模块在 broker 上创建的主题。
     */
    private static final String TOPIC_PREFIX = "aifeimq_";

    /**
     * 消费组、客户端标识与主题前缀共用的随机后缀；每个用例一个新实例，故互不相同。
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
        Assume.assumeTrue("Kafka integration tests: enable -Dkafka.integration=true", ENABLED);
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
     * 以随机消费组 / 客户端标识与指定 broker 地址创建 Kafka 总线；客户端懒连接，构造不校验 broker 连通性，
     * 不可达通常延迟到首个 {@code send} / {@code subscribe} 才失败。
     *
     * @return 新的 Kafka 总线
     */
    @Override
    protected MessageBus newBus() {
        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, KafkaProvider.TYPE);
        // 键名与实现包内的后端专有键一致：bootstrap-servers / group-id / client-id
        properties.setProperty("mq.kafka.bootstrap-servers", BOOTSTRAP_SERVERS);
        properties.setProperty("mq.kafka.group-id", "aifeimq_group_" + instanceId);
        properties.setProperty("mq.kafka.client-id", TOPIC_PREFIX + instanceId);

        MessageBus created = new KafkaMessageBus(MqConfig.parse(properties,
                new MqProviderRegistry().types(), MqConfig.MAIN_INSTANCE_NAME));
        this.bus = created;
        return created;
    }
}
