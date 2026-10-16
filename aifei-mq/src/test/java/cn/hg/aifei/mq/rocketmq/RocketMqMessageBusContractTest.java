/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rocketmq;

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
 * RocketMQ 后端继承运行 {@link MessageBusContractTest} 的<b>共同底线</b>契约。
 *
 * <p><b>默认关闭</b>：未开启时整类经 {@code Assume} 跳过；用 {@code -Drocketmq.integration=true} 开启，
 * 并可用 {@code -Drocketmq.namesrv-addr=host:port} 指定 nameserver（默认 {@code 127.0.0.1:9876}）。
 * 开启后不得以 {@code Assume} 掩盖后端故障：创建总线<b>不校验</b> nameserver 连通性（懒连接），故后端不可达
 * 通常在首个 {@code send} / {@code subscribe} 处使用例失败，而非 {@link #newBus()} 建总线处。</p>
 *
 * <p>隔离：每个用例各自新建总线，故各持有<b>随机消费者组</b>与 {@link #topic(String)} 派生的<b>随机主题前缀</b>，
 * 避免与历史残留消息、并行实例互相干扰；{@link #closeBus()} 兜底关闭总线（基类用例内已关闭，关闭幂等）。
 * broker 侧被自动创建的主题无法经客户端删除，故主题名带随机前缀以便识别与运维清理。</p>
 *
 * <p>后端差异行为（投递模型、有序性、投递保证、{@code messageId} 语义、取消订阅的尽力语义等）见
 * {@code docs/design.md} §5.6 (C)，不属本基类用例。</p>
 */
public class RocketMqMessageBusContractTest extends MessageBusContractTest {

    /**
     * 集成测试开关；未开启时整类跳过。
     */
    private static final boolean ENABLED = Boolean.getBoolean("rocketmq.integration");

    /**
     * nameserver 地址。
     */
    private static final String NAMESRV_ADDR = System.getProperty("rocketmq.namesrv-addr", "127.0.0.1:9876");

    /**
     * 等待上限：真实后端含 broker 往返、消费线程调度与 rebalance，故放宽到 60 秒。
     */
    private static final long AWAIT_TIMEOUT_MILLIS = 60_000L;

    /**
     * 主题随机前缀的固定前缀，便于识别本模块在 broker 上创建的主题。
     */
    private static final String TOPIC_PREFIX = "aifeimq_";

    /**
     * 生产者组 / 消费者组与主题前缀共用的随机后缀；每个用例一个新实例，故每组、每主题互不相同。
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
        Assume.assumeTrue("RocketMQ integration tests: enable -Drocketmq.integration=true", ENABLED);
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
     * 以本用例的随机组名与指定 nameserver 创建 RocketMQ 总线；构造只做本地初始化，<b>不校验</b> nameserver
     * 连通性，不可达通常延迟到首个 {@code send} / {@code subscribe} 才失败。
     *
     * @return 新的 RocketMQ 总线
     */
    @Override
    protected MessageBus newBus() {
        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, RocketMqProvider.TYPE);
        // 键名与实现包内的后端专有键一致：namesrv-addr / producer-group / consumer-group
        properties.setProperty("mq.rocketmq.namesrv-addr", NAMESRV_ADDR);
        properties.setProperty("mq.rocketmq.producer-group", "aifeimq_producer_" + instanceId);
        properties.setProperty("mq.rocketmq.consumer-group", "aifeimq_consumer_" + instanceId);

        MessageBus created = new RocketMqMessageBus(MqConfig.parse(properties, new MqProviderRegistry().types()));
        this.bus = created;
        return created;
    }
}
