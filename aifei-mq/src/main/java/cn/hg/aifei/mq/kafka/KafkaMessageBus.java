/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.kafka;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

/**
 * Kafka 后端总线：<b>构造内</b>创建生产者客户端（客户端自身<b>懒连接</b>）；消费侧<b>延迟到首次订阅</b>才创建消费者、
 * 订阅主题并启动<b>拉取循环线程</b>（以 {@code poll-timeout-ms} 阻塞）。
 *
 * <p>建连时机：链路上 {@code MqPlugin.start()} → {@code buildBus} → 本类构造内创建生产者客户端，但 Kafka 客户端
 * 构造期<b>不探测 broker</b>，故 broker 不可达时构造仍成功——即<b>「启动成功」≠「broker 可达」</b>（与 RocketMQ
 * 同构）；故障延迟到首次 {@code send} 或消费拉取才以 {@link MqException} 暴露。消费侧因订阅主题在装配时未知，
 * 延迟到首次 {@link #subscribe(String, MessageListener)} 创建消费者并订阅，随后启动拉取循环线程。</p>
 *
 * <p>投递与消费：消息 key 恒为 {@code null}，value 为载荷<b>原始字节</b>（固定使用字节序列化器，不引入序列化层）；
 * {@code messageId} 取 broker 返回的<b>分区 + 位点</b>元数据拼装（属 broker 生成）。消费在拉取循环线程内按批拉取、
 * 逐条按本地登记顺序回调，监听器异常就地隔离。位点由消费组（{@code group-id}）维护。</p>
 *
 * <p>{@code unsubscribe} 只解除本地按实例身份的登记（<b>尽力语义</b>）：<b>不</b>离开消费组、<b>不</b>撤销 broker
 * 侧订阅。关闭幂等：{@code close()} 后发送、订阅与取消均视为状态非法，且不再重连。</p>
 */
public class KafkaMessageBus implements MessageBus {

    /**
     * broker 地址列表配置键，形如 {@code host:port}，多个以 {@code ,} 分隔。
     */
    private static final String KEY_BOOTSTRAP_SERVERS = "bootstrap-servers";

    /**
     * 消费组配置键。
     */
    private static final String KEY_GROUP_ID = "group-id";

    /**
     * 无位点 / 位点越界时的起始策略配置键。
     */
    private static final String KEY_AUTO_OFFSET_RESET = "auto-offset-reset";

    /**
     * 生产者应答级别配置键。
     */
    private static final String KEY_ACKS = "acks";

    /**
     * 客户端标识配置键。
     */
    private static final String KEY_CLIENT_ID = "client-id";

    /**
     * 安全协议配置键。
     */
    private static final String KEY_SECURITY_PROTOCOL = "security-protocol";

    /**
     * SASL 机制配置键。
     */
    private static final String KEY_SASL_MECHANISM = "sasl-mechanism";

    /**
     * 认证用户名配置键；只进配置域。
     */
    private static final String KEY_USERNAME = "username";

    /**
     * 认证口令配置键；只进配置域。
     */
    private static final String KEY_PASSWORD = "password";

    /**
     * 完整 JAAS 配置串配置键；凭据整体落此，只进配置域。
     */
    private static final String KEY_SASL_JAAS_CONFIG = "sasl-jaas-config";

    /**
     * 请求超时（毫秒）配置键。
     */
    private static final String KEY_REQUEST_TIMEOUT_MS = "request-timeout-ms";

    /**
     * 连接层重连退避（毫秒）配置键。
     */
    private static final String KEY_RECONNECT_BACKOFF_MS = "reconnect-backoff-ms";

    /**
     * 可重试请求的重试退避（毫秒）配置键。
     */
    private static final String KEY_RETRY_BACKOFF_MS = "retry-backoff-ms";

    /**
     * 消费拉取循环单次阻塞上限（毫秒）配置键。
     */
    private static final String KEY_POLL_TIMEOUT_MS = "poll-timeout-ms";

    /**
     * 未配置消费组时使用的组名。
     */
    private static final String DEFAULT_GROUP_ID = "aifei-mq";

    /**
     * 起始策略默认值：与既有 RocketMQ 后端 {@code CONSUME_FROM_FIRST_OFFSET} 的取舍一致。
     */
    private static final String DEFAULT_AUTO_OFFSET_RESET = "earliest";

    /**
     * 生产者应答级别默认值。
     */
    private static final String DEFAULT_ACKS = "1";

    /**
     * 请求超时默认值（毫秒）。
     */
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 30000;

    /**
     * 连接层重连退避默认值（毫秒）。
     */
    private static final int DEFAULT_RECONNECT_BACKOFF_MS = 50;

    /**
     * 重试退避默认值（毫秒）。
     */
    private static final int DEFAULT_RETRY_BACKOFF_MS = 100;

    /**
     * 拉取循环单次阻塞上限默认值（毫秒）。
     */
    private static final int DEFAULT_POLL_TIMEOUT_MS = 1000;

    /**
     * 关闭客户端与等待拉取线程退出的超时（毫秒）。
     */
    private static final long CLOSE_TIMEOUT_MILLIS = 5000L;

    /**
     * 主题到本地监听器的订阅登记；键集合即已登记主题。
     */
    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 已向消费者登记订阅的主题；由 {@link #consumerLock} 保护。
     */
    private final Set<String> subscribedTopics = new LinkedHashSet<>();

    /**
     * 已登记主题的快照，供拉取循环在无锁下读取；由 {@link #consumerLock} 保护写入。
     */
    private volatile List<String> topicSnapshot = Collections.emptyList();

    /**
     * 生产者视图；构造时创建，与消费者视图共用本总线。
     */
    private final Producer producer = new KafkaProducer(this);

    /**
     * 消费者视图；构造时创建，与生产者视图共用本总线。
     */
    private final Consumer consumer = new KafkaConsumer(this);

    /**
     * Kafka 生产者客户端，构造内创建（客户端懒连接）。
     */
    private final org.apache.kafka.clients.producer.KafkaProducer<byte[], byte[]> producerClient;

    /**
     * 消费者客户端配置，首次订阅时用于创建消费者。
     */
    private final Map<String, Object> consumerProps;

    /**
     * 拉取循环单次阻塞上限（毫秒）。
     */
    private final int pollTimeoutMs;

    /**
     * 保护消费者客户端的创建、订阅登记与启动。
     */
    private final Object consumerLock = new Object();

    /**
     * Kafka 消费者客户端，首次订阅时创建。
     */
    private volatile org.apache.kafka.clients.consumer.KafkaConsumer<byte[], byte[]> consumerClient;

    /**
     * 拉取循环线程，首次订阅时启动。
     */
    private Thread pollThread;

    /**
     * 消费者客户端是否已启动。
     */
    private boolean consumerStarted;

    /**
     * 是否需要重新订阅：新增主题时置位并唤醒拉取循环。
     */
    private volatile boolean reSubscribe;

    /**
     * 是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 读取配置并创建生产者客户端与消费者配置：只对<b>配置</b>错误（缺 {@code bootstrap-servers}、数值键非法）
     * 立即失败；<b>不</b>校验 broker 连通性（客户端懒连接），故 broker 不可达时构造仍成功。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}，或数值键取值非法
     * @throws MqException 未配置 broker 地址，或生产者客户端初始化失败
     */
    public KafkaMessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String bootstrapServers = textValue(config, KEY_BOOTSTRAP_SERVERS);
        if (bootstrapServers == null) {
            throw new MqException("kafka bootstrap servers are required: "
                    + config.prefix() + "." + config.type() + "." + KEY_BOOTSTRAP_SERVERS);
        }
        String groupId = stringValue(config, KEY_GROUP_ID, DEFAULT_GROUP_ID);
        String autoOffsetReset = stringValue(config, KEY_AUTO_OFFSET_RESET, DEFAULT_AUTO_OFFSET_RESET);
        String acks = stringValue(config, KEY_ACKS, DEFAULT_ACKS);
        String clientId = stringValue(config, KEY_CLIENT_ID, generatedClientId());
        String securityProtocol = textValue(config, KEY_SECURITY_PROTOCOL);
        String saslMechanism = textValue(config, KEY_SASL_MECHANISM);
        String username = textValue(config, KEY_USERNAME);
        String password = config.get(KEY_PASSWORD);
        String saslJaasConfig = textValue(config, KEY_SASL_JAAS_CONFIG);
        int requestTimeoutMs = intValue(config, KEY_REQUEST_TIMEOUT_MS, DEFAULT_REQUEST_TIMEOUT_MS);
        int reconnectBackoffMs = intValue(config, KEY_RECONNECT_BACKOFF_MS, DEFAULT_RECONNECT_BACKOFF_MS);
        int retryBackoffMs = intValue(config, KEY_RETRY_BACKOFF_MS, DEFAULT_RETRY_BACKOFF_MS);
        int pollTimeoutMsValue = intValue(config, KEY_POLL_TIMEOUT_MS, DEFAULT_POLL_TIMEOUT_MS);
        requireNonNegative(KEY_REQUEST_TIMEOUT_MS, requestTimeoutMs);
        requireNonNegative(KEY_RECONNECT_BACKOFF_MS, reconnectBackoffMs);
        requireNonNegative(KEY_RETRY_BACKOFF_MS, retryBackoffMs);
        requireNonNegative(KEY_POLL_TIMEOUT_MS, pollTimeoutMsValue);

        String jaasConfig = resolveJaasConfig(saslJaasConfig, saslMechanism, username, password);

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.ACKS_CONFIG, acks);
        producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        producerProps.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, reconnectBackoffMs);
        producerProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, retryBackoffMs);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        applySecurity(producerProps, securityProtocol, saslMechanism, jaasConfig);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.TRUE);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        consumerProps.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, reconnectBackoffMs);
        consumerProps.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, retryBackoffMs);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        applySecurity(consumerProps, securityProtocol, saslMechanism, jaasConfig);

        try {
            this.producerClient = new org.apache.kafka.clients.producer.KafkaProducer<>(producerProps);
        } catch (RuntimeException e) {
            // 客户端配置校验失败属配置错误；异常消息不回显配置取值（可能含凭据）
            throw new MqException("failed to initialize kafka producer", e);
        }
        this.consumerProps = consumerProps;
        this.pollTimeoutMs = pollTimeoutMsValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Producer producer() {
        return producer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Consumer consumer() {
        return consumer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        org.apache.kafka.clients.consumer.KafkaConsumer<byte[], byte[]> client = consumerClient;
        if (client != null) {
            if (consumerStarted) {
                client.wakeup();
            }
            Thread thread = pollThread;
            if (thread != null) {
                try {
                    thread.join(CLOSE_TIMEOUT_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                client.close(Duration.ofMillis(CLOSE_TIMEOUT_MILLIS));
            } catch (RuntimeException e) {
                // 关闭阶段不因故障上抛：连接随对象释放
            }
        }
        try {
            producerClient.close(Duration.ofMillis(CLOSE_TIMEOUT_MILLIS));
        } catch (RuntimeException e) {
            // 同上
        }
        subscriptions.clear();
    }

    /**
     * 发送一条消息到 broker，并返回 broker 分配的<b>分区 + 位点</b>元数据拼装的标识。
     *
     * <p>{@code acks} 生效；客户端 / 网络故障统一映射为 {@link MqException}，中断需回填中断标志。</p>
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 发送失败
     */
    SendResult publish(Message message) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(message.topic(), null, message.payload());
        try {
            Future<RecordMetadata> future = producerClient.send(record);
            RecordMetadata metadata = future.get();
            if (metadata == null) {
                // broker 未返回元数据属运行时故障，而非参数非法
                throw new MqException("kafka send returned no metadata, topic: " + message.topic());
            }
            String messageId = metadata.topic() + "-" + metadata.partition() + "@" + metadata.offset();
            return SendResult.of(message.topic(), messageId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqException("kafka send interrupted, topic: " + message.topic(), e);
        } catch (ExecutionException e) {
            throw new MqException("kafka send failed, topic: " + message.topic(), e);
        } catch (MqException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MqException("kafka send failed, topic: " + message.topic(), e);
        }
    }

    /**
     * 登记监听器：本地登记后，首次调用会创建消费者、订阅当前全部主题并启动拉取循环线程；此后新增主题会触发
     * 重新订阅（经 {@code wakeup} 交由拉取循环执行，KafkaConsumer 非线程安全）。
     *
     * @param topic 主题，即 Kafka topic 名
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 消费者创建或订阅失败
     */
    void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        synchronized (consumerLock) {
            if (closed) {
                throw new IllegalStateException("message bus has been closed");
            }
            subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
            boolean newTopic = subscribedTopics.add(topic);
            if (newTopic) {
                topicSnapshot = new ArrayList<>(subscribedTopics);
            }
            if (!consumerStarted) {
                org.apache.kafka.clients.consumer.KafkaConsumer<byte[], byte[]> created;
                try {
                    created = new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProps);
                    created.subscribe(topicSnapshot);
                } catch (RuntimeException e) {
                    throw new MqException("failed to start kafka consumer", e);
                }
                this.consumerClient = created;
                Thread thread = new Thread(this::pollLoop, "aifei-mq-kafka-consumer");
                thread.setDaemon(true);
                this.pollThread = thread;
                this.consumerStarted = true;
                thread.start();
            } else if (newTopic) {
                reSubscribe = true;
                consumerClient.wakeup();
            }
        }
    }

    /**
     * 取消订阅登记：从该主题的登记列表中按<b>实例身份</b>移除该监听器实例（不使用 {@code equals}）。
     *
     * <p>属尽力语义：只解除本进程的本地登记，<b>不</b>离开消费组、<b>不</b>撤销 broker 侧订阅，故同主题其它本地
     * 监听器与消费组位点不受影响；目标主题未登记或列表内无该实例时静默返回。</p>
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     */
    void unsubscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        List<MessageListener> listeners = subscriptions.get(topic);
        if (listeners == null) {
            return;
        }
        listeners.removeIf(e -> e == listener);
    }

    /**
     * 拉取循环：以 {@code poll-timeout-ms} 阻塞拉取，逐条按本地登记顺序派发；被 {@code wakeup} 打断时按状态判定
     * 是重新订阅还是退出。
     */
    private void pollLoop() {
        while (!closed) {
            try {
                if (reSubscribe) {
                    reSubscribe = false;
                    consumerClient.subscribe(topicSnapshot);
                }
                ConsumerRecords<byte[], byte[]> records = consumerClient.poll(Duration.ofMillis(pollTimeoutMs));
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    dispatch(record);
                }
            } catch (WakeupException e) {
                // 唤醒用于新增订阅或关闭：由循环顶部的状态判定继续或退出
            } catch (RuntimeException e) {
                // 拉取循环故障就地隔离：退出循环避免空转打爆 CPU（监听器异常已在 dispatch 内单独隔离）
                return;
            }
        }
    }

    /**
     * 派发一条拉取到的记录：按本地登记顺序回调主题的监听器，监听器异常就地隔离。
     *
     * @param record 拉取到的记录
     */
    private void dispatch(ConsumerRecord<byte[], byte[]> record) {
        byte[] payload = record.value();
        if (payload == null) {
            return;
        }
        List<MessageListener> listeners = subscriptions.get(record.topic());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        Message message = Message.of(record.topic(), payload);
        for (MessageListener listener : listeners) {
            try {
                listener.onMessage(message);
            } catch (RuntimeException e) {
                // 监听器异常就地隔离：不中断其余订阅者、不上抛给生产者
            }
        }
    }

    /**
     * 生成客户端标识：未配置 {@code client-id} 时使用，保证非空。
     *
     * @return 客户端标识
     */
    private static String generatedClientId() {
        return KafkaProvider.TYPE + "-" + UUID.randomUUID();
    }

    /**
     * 组装生效的 JAAS 配置：显式配置 {@code sasl-jaas-config} 时原样使用；否则在给定机制与用户名时按机制拼装。
     *
     * @param jaasConfig 显式 JAAS 配置串，可为 {@code null}
     * @param mechanism SASL 机制，可为 {@code null}
     * @param username 用户名，可为 {@code null}
     * @param password 口令，可为 {@code null}
     * @return 生效的 JAAS 配置串，无法拼装时为 {@code null}
     */
    private static String resolveJaasConfig(String jaasConfig, String mechanism, String username, String password) {
        if (jaasConfig != null) {
            return jaasConfig;
        }
        if (mechanism == null || username == null) {
            return null;
        }
        String loginModule;
        if ("PLAIN".equalsIgnoreCase(mechanism)) {
            loginModule = "org.apache.kafka.common.security.plain.PlainLoginModule";
        } else if ("SCRAM-SHA-256".equalsIgnoreCase(mechanism) || "SCRAM-SHA-512".equalsIgnoreCase(mechanism)) {
            loginModule = "org.apache.kafka.common.security.scram.ScramLoginModule";
        } else {
            return null;
        }
        return loginModule + " required username=\"" + username + "\" password=\""
                + (password == null ? "" : password) + "\";";
    }

    /**
     * 把安全协议 / SASL 机制 / JAAS 配置写入客户端配置：均未配置时不写入，交由客户端默认。
     *
     * @param props 客户端配置
     * @param securityProtocol 安全协议，可为 {@code null}
     * @param saslMechanism SASL 机制，可为 {@code null}
     * @param jaasConfig JAAS 配置串，可为 {@code null}
     */
    private static void applySecurity(Map<String, Object> props, String securityProtocol, String saslMechanism,
            String jaasConfig) {
        if (securityProtocol != null) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        }
        if (saslMechanism != null) {
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }
        if (jaasConfig != null) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
        }
    }

    /**
     * 读取配置值并去除首尾空白；未配置或只含空白时返回 {@code null}。
     *
     * @param config 配置对象
     * @param key 相对键名
     * @return 去空白后的配置值，或 {@code null}
     */
    private static String textValue(MqConfig config, String key) {
        String value = config.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 读取字符串配置：未配置或只含空白时取默认值。
     *
     * @param config 配置对象
     * @param key 相对键名
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    private static String stringValue(MqConfig config, String key, String defaultValue) {
        String value = textValue(config, key);
        return (value == null) ? defaultValue : value;
    }

    /**
     * 读取整型配置：未配置时取默认值。
     *
     * @param config 配置对象
     * @param key 相对键名
     * @param defaultValue 默认值
     * @return 配置值或默认值
     * @throws IllegalArgumentException 取值不是整型字面量
     */
    private static int intValue(MqConfig config, String key, int defaultValue) {
        String value = textValue(config, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("kafka config value must be an integer: " + key + "=" + value, e);
        }
    }

    /**
     * 校验整型配置非负。
     *
     * @param key 相对键名，仅用于异常消息（不拼接凭据取值）
     * @param value 配置值
     * @throws IllegalArgumentException 取值为负
     */
    private static void requireNonNegative(String key, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("kafka config value must not be negative: " + key + "=" + value);
        }
    }
}
