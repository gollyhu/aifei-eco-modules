/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rabbitmq;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RabbitMQ 后端总线（实验性，未真机验证）：构造内建立连接与发布通道（eager，<b>启动期暴露连通性故障</b>），
 * 消费侧延迟到首次订阅才声明队列并开始投递。
 *
 * <p>{@code topic} 即<b>队列名</b>（默认交换机下同时是 routing key）；队列名在装配时未知，故延迟到首次
 * {@link #subscribe(String, MessageListener)} 才按 {@code durable} 声明队列、在配置了 {@code exchange} 时声明
 * 交换机并绑定。默认交换机下 publish 不校验队列是否存在，<b>未声明队列时消息被 broker 静默丢弃</b>而发送仍报成功。</p>
 *
 * <p>未开 {@code publisher-confirms} 时 {@code messageId} 由适配器<b>本地生成</b>，格式
 * {@code rabbitmq:<host>:<port>:<序号>}；开启时改用通道的<b>发布确认序号</b>并同步等待确认。投递以手动确认闭环，
 * 未确认即断开时 broker 会重投（at-least-once，监听器需幂等）。{@code unsubscribe} 只解除本地登记，不发取消投递
 * 指令。共同底线与允许差异见 {@code docs/design.md} §5.6。</p>
 */
public class RabbitMqMessageBus implements MessageBus {

    /** broker 主机配置键。 */
    private static final String KEY_HOST = "host";

    /** broker 端口配置键。 */
    private static final String KEY_PORT = "port";

    /** 虚拟主机配置键。 */
    private static final String KEY_VIRTUAL_HOST = "virtual-host";

    /** 认证用户名配置键；只进配置域。 */
    private static final String KEY_USERNAME = "username";

    /** 认证口令配置键；只进配置域。 */
    private static final String KEY_PASSWORD = "password";

    /** 交换机配置键；为空表示走默认交换机，此时 {@code topic} 即队列名。 */
    private static final String KEY_EXCHANGE = "exchange";

    /** 交换机类型配置键；仅在配置了 {@code exchange} 时生效。 */
    private static final String KEY_EXCHANGE_TYPE = "exchange-type";

    /** 声明队列时是否持久化的配置键。 */
    private static final String KEY_DURABLE = "durable";

    /** 消费预取上限配置键；{@code 0} 表示不限。 */
    private static final String KEY_PREFETCH = "prefetch";

    /** 发布确认开关配置键。 */
    private static final String KEY_PUBLISHER_CONFIRMS = "publisher-confirms";

    /** 建连超时（毫秒）配置键。 */
    private static final String KEY_CONNECTION_TIMEOUT_MILLIS = "connection-timeout-millis";

    /** 是否启用客户端原生自动恢复的配置键。 */
    private static final String KEY_AUTO_RECONNECT = "auto-reconnect";

    /** 网络恢复重试间隔（毫秒）配置键。 */
    private static final String KEY_NETWORK_RECOVERY_INTERVAL_MILLIS = "network-recovery-interval-millis";

    /** 恢复后是否自动重建队列 / 绑定等拓扑的配置键。 */
    private static final String KEY_TOPOLOGY_RECOVERY = "topology-recovery";

    /** 默认交换机：{@code exchange} 未配置时 basicPublish 的交换机名。 */
    private static final String DEFAULT_EXCHANGE = "";

    // 其余默认值与上方键名逐一对应（port / virtual-host / exchange-type / durable / prefetch /
    // publisher-confirms / connection-timeout-millis / auto-reconnect /
    // network-recovery-interval-millis / topology-recovery）
    private static final int DEFAULT_PORT = 5672;

    // 端口合法区间
    private static final int MIN_PORT = 1;

    private static final int MAX_PORT = 65535;

    private static final String DEFAULT_VIRTUAL_HOST = "/";

    private static final String DEFAULT_EXCHANGE_TYPE = "topic";

    private static final boolean DEFAULT_DURABLE = true;

    private static final int DEFAULT_PREFETCH = 0;

    private static final boolean DEFAULT_PUBLISHER_CONFIRMS = false;

    private static final int DEFAULT_CONNECTION_TIMEOUT_MILLIS = 60000;

    private static final boolean DEFAULT_AUTO_RECONNECT = true;

    private static final int DEFAULT_NETWORK_RECOVERY_INTERVAL_MILLIS = 5000;

    private static final boolean DEFAULT_TOPOLOGY_RECOVERY = true;

    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /** 已声明并按队列登记投递的队列；由 {@link #consumerLock} 保护。 */
    private final Set<String> consumedQueues = new HashSet<>();

    /** 本地 messageId 序号（仅在未开发布确认时使用）。 */
    private final AtomicLong messageSeq = new AtomicLong();

    private final Connection connection;

    /** 发布专用通道，构造内建立；开启发布确认时已置为确认模式。 */
    private final Channel publishChannel;

    /** 未配置时为默认交换机（空串）。 */
    private final String exchange;

    private final String exchangeType;

    private final boolean durable;

    private final int prefetch;

    private final boolean publisherConfirms;

    /** 本地 messageId 前缀，形如 {@code rabbitmq:<host>:<port>:}。 */
    private final String messageIdPrefix;

    /** 保护消费通道的创建与逐队列的声明 / 投递登记。 */
    private final Object consumerLock = new Object();

    /** 首次订阅时创建。 */
    private Channel consumeChannel;

    /** 消费通道是否已创建。 */
    private boolean consumerStarted;

    private volatile boolean closed;

    /**
     * 读取配置并<b>立即</b>建立连接与发布通道：配置错误与 broker 不可达都在构造期失败；消费通道延迟到首次订阅。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}，或数值键取值非法
     * @throws MqException 未配置 broker 主机，或建连 / 创建发布通道失败（broker 不可达）
     */
    public RabbitMqMessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String host = textValue(config, KEY_HOST);
        if (host == null) {
            throw new MqException("rabbitmq host is required: "
                    + config.prefix() + "." + config.type() + "." + KEY_HOST);
        }
        int port = intValue(config, KEY_PORT, DEFAULT_PORT);
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("rabbitmq config value must be within " + MIN_PORT + "-" + MAX_PORT
                    + ": " + KEY_PORT + "=" + port);
        }
        String virtualHost = stringValue(config, KEY_VIRTUAL_HOST, DEFAULT_VIRTUAL_HOST);
        String username = textValue(config, KEY_USERNAME);
        String password = config.get(KEY_PASSWORD);
        String configuredExchange = textValue(config, KEY_EXCHANGE);
        String configuredExchangeType = stringValue(config, KEY_EXCHANGE_TYPE, DEFAULT_EXCHANGE_TYPE);
        boolean durableValue = booleanValue(config, KEY_DURABLE, DEFAULT_DURABLE);
        int prefetchValue = intValue(config, KEY_PREFETCH, DEFAULT_PREFETCH);
        boolean publisherConfirmsValue = booleanValue(config, KEY_PUBLISHER_CONFIRMS, DEFAULT_PUBLISHER_CONFIRMS);
        int connectionTimeoutMillis = intValue(config, KEY_CONNECTION_TIMEOUT_MILLIS,
                DEFAULT_CONNECTION_TIMEOUT_MILLIS);
        boolean autoReconnect = booleanValue(config, KEY_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT);
        int networkRecoveryIntervalMillis = intValue(config, KEY_NETWORK_RECOVERY_INTERVAL_MILLIS,
                DEFAULT_NETWORK_RECOVERY_INTERVAL_MILLIS);
        boolean topologyRecovery = booleanValue(config, KEY_TOPOLOGY_RECOVERY, DEFAULT_TOPOLOGY_RECOVERY);
        requireNonNegative(KEY_PREFETCH, prefetchValue);
        requireNonNegative(KEY_CONNECTION_TIMEOUT_MILLIS, connectionTimeoutMillis);
        requireNonNegative(KEY_NETWORK_RECOVERY_INTERVAL_MILLIS, networkRecoveryIntervalMillis);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setVirtualHost(virtualHost);
        if (username != null) {
            factory.setUsername(username);
        }
        if (password != null && !password.isEmpty()) {
            factory.setPassword(password);
        }
        factory.setConnectionTimeout(connectionTimeoutMillis);
        factory.setAutomaticRecoveryEnabled(autoReconnect);
        factory.setTopologyRecoveryEnabled(topologyRecovery);
        factory.setNetworkRecoveryInterval(networkRecoveryIntervalMillis);

        Connection createdConnection;
        try {
            createdConnection = factory.newConnection();
        } catch (IOException e) {
            throw new MqException("failed to connect rabbitmq broker: " + host + ":" + port, e);
        } catch (TimeoutException e) {
            throw new MqException("timed out connecting rabbitmq broker: " + host + ":" + port, e);
        }
        this.connection = createdConnection;

        Channel createdChannel;
        try {
            createdChannel = createdConnection.createChannel();
            if (publisherConfirmsValue) {
                createdChannel.confirmSelect();
            }
        } catch (IOException e) {
            closeQuietly(createdConnection);
            throw new MqException("failed to create rabbitmq publish channel: " + host + ":" + port, e);
        }
        this.publishChannel = createdChannel;
        this.exchange = (configuredExchange == null) ? DEFAULT_EXCHANGE : configuredExchange;
        this.exchangeType = configuredExchangeType;
        this.durable = durableValue;
        this.prefetch = prefetchValue;
        this.publisherConfirms = publisherConfirmsValue;
        this.messageIdPrefix = RabbitMqProvider.TYPE + ":" + host + ":" + port + ":";
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        synchronized (consumerLock) {
            if (consumeChannel != null) {
                closeQuietly(consumeChannel);
                consumeChannel = null;
            }
            consumerStarted = false;
            consumedQueues.clear();
        }
        closeQuietly(publishChannel);
        closeQuietly(connection);
        subscriptions.clear();
    }

    /**
     * 发布一条消息：routing key 取 {@code topic}（默认交换机下即队列名）；故障与确认失败统一映射为
     * {@link MqException}（中断回填中断标志）。
     *
     * @param message 消息
     * @return 发送结果，messageId 为本地生成序号或通道的发布确认序号
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 发布失败或未被确认
     */
    @Override
    public SendResult publish(Message message) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        try {
            long seq = publisherConfirms ? publishChannel.getNextPublishSeqNo() : messageSeq.incrementAndGet();
            publishChannel.basicPublish(exchange, message.topic(), null, message.payload());
            if (publisherConfirms && !publishChannel.waitForConfirms()) {
                throw new MqException("rabbitmq publish was not confirmed, topic: " + message.topic());
            }
            return SendResult.of(message.topic(), messageIdPrefix + seq);
        } catch (IOException e) {
            throw new MqException("rabbitmq send failed, topic: " + message.topic(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqException("rabbitmq send interrupted, topic: " + message.topic(), e);
        } catch (MqException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MqException("rabbitmq send failed, topic: " + message.topic(), e);
        }
    }

    /**
     * 登记监听器：本地登记后，若该队列尚未登记投递，则声明队列（配置了 {@code exchange} 时再声明交换机并绑定）
     * 并开始投递；首次调用还会创建消费通道并设置预取上限。
     *
     * @param topic 主题，即队列名
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 创建消费通道、声明队列 / 交换机 / 绑定或开始投递失败
     */
    @Override
    public void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        synchronized (consumerLock) {
            if (closed) {
                throw new IllegalStateException("message bus has been closed");
            }
            Channel channel = consumeChannel();
            List<MessageListener> listeners = subscriptions.computeIfAbsent(topic,
                    key -> new CopyOnWriteArrayList<>());
            listeners.add(listener);
            if (consumedQueues.add(topic)) {
                try {
                    channel.queueDeclare(topic, durable, false, false, null);
                    if (!exchange.isEmpty()) {
                        channel.exchangeDeclare(exchange, exchangeType, durable);
                        channel.queueBind(topic, exchange, topic);
                    }
                } catch (IOException e) {
                    consumedQueues.remove(topic);
                    listeners.removeIf(current -> current == listener);
                    throw new MqException("failed to declare rabbitmq queue: " + topic, e);
                }
                try {
                    channel.basicConsume(topic, false, new RabbitMqDeliveryListener(topic), consumerTag -> {
                        // broker 侧取消投递（如队列被删除）不作本地重登记：取消订阅在本模块恒为本地语义
                    });
                } catch (IOException e) {
                    consumedQueues.remove(topic);
                    listeners.removeIf(current -> current == listener);
                    throw new MqException("failed to consume rabbitmq queue: " + topic, e);
                }
            }
        }
    }

    /**
     * 按<b>实例身份</b>移除该监听器，只解除本地绑定（尽力语义：不向 broker 发送取消投递指令）；目标不存在时
     * 静默返回。
     *
     * @param topic 主题，即队列名
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     */
    @Override
    public void unsubscribe(String topic, MessageListener listener) {
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
     * 取消费通道，首次调用时创建并设置预取上限；须在 {@link #consumerLock} 内调用。
     *
     * @return 消费通道
     * @throws MqException 创建消费通道失败
     */
    private Channel consumeChannel() {
        if (!consumerStarted) {
            try {
                Channel created = connection.createChannel();
                created.basicQos(prefetch);
                this.consumeChannel = created;
                this.consumerStarted = true;
            } catch (IOException e) {
                throw new MqException("failed to start rabbitmq consumer: " + e.getMessage(), e);
            }
        }
        return consumeChannel;
    }

    /**
     * 关闭通道或连接，忽略关闭期故障：关闭失败不改变「已关闭」这一对外可见状态。
     *
     * @param closeable 待关闭对象，可为 {@code null}
     */
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            // 关闭阶段不因故障而上抛：资源随连接关闭一并释放
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
     * 读取布尔配置：未配置时取默认值，取值必须是 {@code true} / {@code false}（忽略大小写）。
     *
     * @param config 配置对象
     * @param key 相对键名
     * @param defaultValue 默认值
     * @return 配置值或默认值
     * @throws IllegalArgumentException 取值不是布尔字面量
     */
    private static boolean booleanValue(MqConfig config, String key, boolean defaultValue) {
        String value = textValue(config, key);
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("rabbitmq config value must be true or false: " + key);
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
            throw new IllegalArgumentException("rabbitmq config value must be an integer: " + key + "=" + value, e);
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
            throw new IllegalArgumentException("rabbitmq config value must not be negative: " + key + "=" + value);
        }
    }

    /**
     * 消费适配器：在客户端投递线程内按本地登记顺序派发某个队列的消息，就地隔离监听器异常，派发后手动确认。
     */
    private final class RabbitMqDeliveryListener implements DeliverCallback {

        /** 负责派发的队列，同时是本地登记表的键。 */
        private final String queue;

        /**
         * @param queue 该适配器对应的队列
         */
        RabbitMqDeliveryListener(String queue) {
            this.queue = queue;
        }

        /**
         * 按本地登记顺序派发，监听器异常就地隔离，随后确认该投递。
         *
         * @param consumerTag 消费者标识
         * @param delivery 投递内容
         */
        @Override
        public void handle(String consumerTag, Delivery delivery) {
            List<MessageListener> listeners = subscriptions.get(queue);
            if (listeners != null && !listeners.isEmpty()) {
                Message message = Message.of(delivery.getEnvelope().getRoutingKey(), delivery.getBody());
                for (MessageListener listener : listeners) {
                    try {
                        listener.onMessage(message);
                    } catch (RuntimeException e) {
                        // 监听器异常就地隔离：不中断其余订阅者、不上抛给生产者，也不触发 broker 重投
                    }
                }
            }
            acknowledge(delivery);
        }

        /**
         * 手动确认一条投递；通道已关闭时静默返回——broker 会按未确认处理，属 at-least-once 的正常表现。
         *
         * @param delivery 投递内容
         */
        private void acknowledge(Delivery delivery) {
            Channel channel = consumeChannel;
            if (channel == null) {
                return;
            }
            try {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (IOException e) {
                // 通道已关闭：不再重试确认，交由 broker 的重投机制处理
            }
        }
    }
}
