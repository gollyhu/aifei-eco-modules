/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import cn.hg.aifei.mq.kafka.KafkaMessageBus;
import cn.hg.aifei.mq.kafka.KafkaProvider;
import cn.hg.aifei.mq.mqtt.MqttMessageBus;
import cn.hg.aifei.mq.mqtt.MqttProvider;
import cn.hg.aifei.mq.mqtt5.Mqtt5MessageBus;
import cn.hg.aifei.mq.mqtt5.Mqtt5Provider;
import cn.hg.aifei.mq.rabbitmq.RabbitMqMessageBus;
import cn.hg.aifei.mq.rabbitmq.RabbitMqProvider;
import cn.hg.aifei.mq.redisstream.RedisStreamMessageBus;
import cn.hg.aifei.mq.redisstream.RedisStreamProvider;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;

/**
 * 5 个新后端（{@code mqtt} / {@code mqtt5} / {@code rabbitmq} / {@code kafka} / {@code redisstream}）的
 * <b>配置校验</b>用例：缺少后端必填键时，总线构造必须在<b>任何建连动作之前</b>以 {@link MqException} 快速失败。
 *
 * <p>本类<b>默认执行</b>、<b>不建连</b>、<b>无外部中间件依赖</b>：五处必填键校验（{@code mqtt}/{@code mqtt5}
 * 的 {@code broker-url}、{@code rabbitmq} 的 {@code host}、{@code kafka} 的 {@code bootstrap-servers}、
 * {@code redisstream} 的 {@code host}）都发生在创建客户端 / 建连<b>之前</b>，故缺键时用例立即失败而不阻塞，
 * 无须依赖 broker 可达，也无需以极小超时兜底。</p>
 */
public class BackendConfigValidationTest {

    /**
     * 已注册类型集合，取真实注册表，保证断言与实现同步。
     */
    private static final Set<String> REGISTERED = new MqProviderRegistry().types();

    /**
     * MQTT v3.1.1：缺 {@code broker-url} 时构造立即抛 {@link MqException}（在建连之前）。
     */
    @Test
    public void mqttShouldFailFastWhenBrokerUrlMissing() {
        MqConfig config = configOf(MqttProvider.TYPE);

        assertThrows(MqException.class, () -> new MqttMessageBus(config));
    }

    /**
     * MQTT v5.0：缺 {@code broker-url} 时构造立即抛 {@link MqException}（在建连之前）。
     */
    @Test
    public void mqtt5ShouldFailFastWhenBrokerUrlMissing() {
        MqConfig config = configOf(Mqtt5Provider.TYPE);

        assertThrows(MqException.class, () -> new Mqtt5MessageBus(config));
    }

    /**
     * RabbitMQ：缺 {@code host} 时构造立即抛 {@link MqException}（在建连之前）。
     */
    @Test
    public void rabbitMqShouldFailFastWhenHostMissing() {
        MqConfig config = configOf(RabbitMqProvider.TYPE);

        assertThrows(MqException.class, () -> new RabbitMqMessageBus(config));
    }

    /**
     * Kafka：缺 {@code bootstrap-servers} 时构造立即抛 {@link MqException}（在创建生产者客户端之前）。
     */
    @Test
    public void kafkaShouldFailFastWhenBootstrapServersMissing() {
        MqConfig config = configOf(KafkaProvider.TYPE);

        assertThrows(MqException.class, () -> new KafkaMessageBus(config));
    }

    /**
     * Redis Streams：缺 {@code host} 时构造立即抛 {@link MqException}（在创建连接池之前）。
     */
    @Test
    public void redisStreamShouldFailFastWhenHostMissing() {
        MqConfig config = configOf(RedisStreamProvider.TYPE);

        assertThrows(MqException.class, () -> new RedisStreamMessageBus(config));
    }

    /**
     * 5 个新后端标识都已注册到 provider 注册表（类型白名单包含它们），支撑上述用例能经
     * {@link MqConfig#parse(Properties, Set, String)} 解析出对应类型。
     */
    @Test
    public void shouldRegisterAllFiveNewBackendTypes() {
        assertTrue("mqtt 应已注册", REGISTERED.contains(MqttProvider.TYPE));
        assertTrue("mqtt5 应已注册", REGISTERED.contains(Mqtt5Provider.TYPE));
        assertTrue("rabbitmq 应已注册", REGISTERED.contains(RabbitMqProvider.TYPE));
        assertTrue("kafka 应已注册", REGISTERED.contains(KafkaProvider.TYPE));
        assertTrue("redisstream 应已注册", REGISTERED.contains(RedisStreamProvider.TYPE));
    }

    /**
     * 构造只声明后端类型、<b>不含该后端任何必填键</b>的配置对象。
     *
     * @param type 后端类型标识
     * @return 仅含 {@code mq.type} 的配置对象
     */
    private static MqConfig configOf(String type) {
        Properties properties = new Properties();
        properties.setProperty(MqConfig.TYPE_KEY, type);
        return MqConfig.parse(properties, REGISTERED, MqConfig.MAIN_INSTANCE_NAME);
    }
}
