/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.mqtt;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * MQTT v3.1.1 后端总线：生产与消费共用<b>单一客户端</b>（一条连接），构造内即创建客户端并建连，
 * 消息由客户端网络 / 回调线程驱动派发。
 *
 * <p>启动时机：3.1.1 的客户端模型是「一个 {@code client-id} 一条连接」，故不存在「生产者先启动、消费者
 * 延迟到首次订阅」的分段——{@link #MqttMessageBus(MqConfig)} 内即创建客户端并<b>建立连接</b>；broker
 * 不可达时构造期建连失败，映射为 {@link MqException} 并使装配回滚（<b>启动期 fail-fast</b>，与 RocketMQ
 * 的懒连接相反）。订阅只做「本地登记 + 向客户端登记该 topic 过滤器」。</p>
 *
 * <p>投递与消费：{@code publish} 以配置的 {@code qos} / {@code retain} 发布载荷<b>原始字节</b>；broker
 * 不回消息标识，故 {@code messageId} 由本适配器<b>本地生成</b>，格式 {@code mqtt:<生效 client-id>:<序号>}
 * （序号在本总线内自 1 起单调递增）；<b>不得</b>以 QoS packet identifier 充当 {@code messageId}。消费回调在
 * <b>客户端回调线程</b>内按本地登记顺序派发，单个监听器抛出的运行时异常<b>就地隔离</b>（不中断同主题其它
 * 订阅者、不上抛）。{@code subscribe} 的 topic 为 MQTT <b>过滤器</b>，支持 {@code +} / {@code #} 通配；
 * {@code unsubscribe} 只从本地登记表按<b>实例身份</b>移除（尽力语义，不向 broker 发送取消订阅）。</p>
 *
 * <p>连接恢复：启用客户端<b>原生</b>自动重连（{@code auto-reconnect}，退避上限经 {@code reconnect-delay-max-millis}
 * 设置），本模块不自建重连循环。Paho v3 客户端未提供「退避<b>起始</b>间隔」的公开 API，故
 * {@code reconnect-delay-millis} 只做取值合法性校验、<b>无直接落点</b>。</p>
 *
 * <p>关闭幂等：{@code close()} 后发送、订阅与取消均视为状态非法；关闭后不再重连。</p>
 */
public class MqttMessageBus implements MessageBus {

    /**
     * broker 地址配置键，客户端口径的 server URI，形如 {@code tcp://host:1883}。
     */
    private static final String KEY_BROKER_URL = "broker-url";

    /**
     * 客户端标识配置键；未配置时由客户端生成。
     */
    private static final String KEY_CLIENT_ID = "client-id";

    /**
     * v3 clean session 标志配置键。
     */
    private static final String KEY_CLEAN_SESSION = "clean-session";

    /**
     * 订阅与发布的默认 QoS 配置键。
     */
    private static final String KEY_QOS = "qos";

    /**
     * 发布是否设置 retain 标志的配置键。
     */
    private static final String KEY_RETAIN = "retain";

    /**
     * 认证用户名配置键；只进配置域。
     */
    private static final String KEY_USERNAME = "username";

    /**
     * 认证口令配置键；只进配置域。
     */
    private static final String KEY_PASSWORD = "password";

    /**
     * 心跳间隔（秒）配置键。
     */
    private static final String KEY_KEEP_ALIVE_SECONDS = "keep-alive-seconds";

    /**
     * 连接超时（秒）配置键。
     */
    private static final String KEY_CONNECTION_TIMEOUT_SECONDS = "connection-timeout-seconds";

    /**
     * 是否启用客户端原生自动重连的配置键。
     */
    private static final String KEY_AUTO_RECONNECT = "auto-reconnect";

    /**
     * 重连退避<b>起始</b>间隔（毫秒）配置键；Paho v3 无对应 API，本键只做取值合法性校验。
     */
    private static final String KEY_RECONNECT_DELAY_MILLIS = "reconnect-delay-millis";

    /**
     * 重连退避<b>上限</b>（毫秒）配置键。
     */
    private static final String KEY_RECONNECT_DELAY_MAX_MILLIS = "reconnect-delay-max-millis";

    /**
     * clean session 默认值。
     */
    private static final boolean DEFAULT_CLEAN_SESSION = true;

    /**
     * QoS 默认值。
     */
    private static final int DEFAULT_QOS = 0;

    /**
     * retain 默认值。
     */
    private static final boolean DEFAULT_RETAIN = false;

    /**
     * 心跳间隔默认值（秒）。
     */
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    /**
     * 连接超时默认值（秒）。
     */
    private static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30;

    /**
     * 自动重连默认值。
     */
    private static final boolean DEFAULT_AUTO_RECONNECT = true;

    /**
     * 重连退避起始间隔默认值（毫秒）。
     */
    private static final int DEFAULT_RECONNECT_DELAY_MILLIS = 1000;

    /**
     * 重连退避上限默认值（毫秒）。
     */
    private static final int DEFAULT_RECONNECT_DELAY_MAX_MILLIS = 30000;

    /**
     * QoS 合法下界。
     */
    private static final int MIN_QOS = 0;

    /**
     * QoS 合法上界。
     */
    private static final int MAX_QOS = 2;

    /**
     * topic 过滤器到本地监听器的订阅登记；键集合即已登记的过滤器。
     */
    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 已向客户端登记订阅的 topic 过滤器，用于避免同过滤器重复登记导致重复派发。
     */
    private final Set<String> registeredFilters = ConcurrentHashMap.newKeySet();

    /**
     * 本总线内本地 {@code messageId} 序号，自 1 起单调递增。
     */
    private final AtomicLong messageSeq = new AtomicLong();

    /**
     * 生产者视图；构造时创建，与消费者视图共用本总线。
     */
    private final Producer producer = new MqttProducer(this);

    /**
     * 消费者视图；构造时创建，与生产者视图共用本总线。
     */
    private final Consumer consumer = new MqttConsumer(this);

    /**
     * 生产与消费共用的 MQTT 客户端，构造内已建连。
     */
    private final MqttClient client;

    /**
     * 订阅与发布使用的 QoS。
     */
    private final int qos;

    /**
     * 发布是否设置 retain 标志。
     */
    private final boolean retain;

    /**
     * 本地 {@code messageId} 前缀，形如 {@code mqtt:<生效 client-id>:}。
     */
    private final String messageIdPrefix;

    /**
     * 是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 读取配置、创建唯一客户端并<b>立即建连</b>：配置错误（缺 broker 地址、数值键非法）与连通性故障
     * （broker 不可达）都在构造期立即失败，不留半装配。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}，或数值键取值非法
     * @throws MqException 未配置 broker 地址，或客户端创建 / 建连失败（broker 不可达）
     */
    public MqttMessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String brokerUrl = textValue(config, KEY_BROKER_URL);
        if (brokerUrl == null) {
            throw new MqException("mqtt broker url is required: "
                    + config.prefix() + "." + config.type() + "." + KEY_BROKER_URL);
        }
        String clientId = textValue(config, KEY_CLIENT_ID);
        boolean cleanSession = booleanValue(config, KEY_CLEAN_SESSION, DEFAULT_CLEAN_SESSION);
        int qosValue = intValue(config, KEY_QOS, DEFAULT_QOS);
        if (qosValue < MIN_QOS || qosValue > MAX_QOS) {
            throw new IllegalArgumentException("mqtt config value must be " + MIN_QOS + ", 1 or " + MAX_QOS
                    + ": " + KEY_QOS + "=" + qosValue);
        }
        boolean retainValue = booleanValue(config, KEY_RETAIN, DEFAULT_RETAIN);
        String username = textValue(config, KEY_USERNAME);
        String password = config.get(KEY_PASSWORD);
        int keepAliveSeconds = intValue(config, KEY_KEEP_ALIVE_SECONDS, DEFAULT_KEEP_ALIVE_SECONDS);
        int connectionTimeoutSeconds = intValue(config, KEY_CONNECTION_TIMEOUT_SECONDS,
                DEFAULT_CONNECTION_TIMEOUT_SECONDS);
        boolean autoReconnect = booleanValue(config, KEY_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT);
        int reconnectDelayMaxMillis = intValue(config, KEY_RECONNECT_DELAY_MAX_MILLIS,
                DEFAULT_RECONNECT_DELAY_MAX_MILLIS);
        // Paho v3 未提供「退避起始间隔」的公开 API，本键只校验取值合法性（起始间隔由客户端固定承担）
        int reconnectDelayMillis = intValue(config, KEY_RECONNECT_DELAY_MILLIS, DEFAULT_RECONNECT_DELAY_MILLIS);
        requireNonNegative(KEY_KEEP_ALIVE_SECONDS, keepAliveSeconds);
        requireNonNegative(KEY_CONNECTION_TIMEOUT_SECONDS, connectionTimeoutSeconds);
        requireNonNegative(KEY_RECONNECT_DELAY_MILLIS, reconnectDelayMillis);
        requireNonNegative(KEY_RECONNECT_DELAY_MAX_MILLIS, reconnectDelayMaxMillis);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(cleanSession);
        options.setKeepAliveInterval(keepAliveSeconds);
        options.setConnectionTimeout(connectionTimeoutSeconds);
        options.setAutomaticReconnect(autoReconnect);
        if (autoReconnect) {
            options.setMaxReconnectDelay(reconnectDelayMaxMillis);
        }
        if (username != null) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.toCharArray());
        }

        MqttClient createdClient;
        try {
            String effectiveClientId = (clientId == null) ? MqttClient.generateClientId() : clientId;
            createdClient = new MqttClient(brokerUrl, effectiveClientId);
            createdClient.connect(options);
        } catch (MqttException e) {
            throw new MqException("failed to connect mqtt broker: " + e.getMessage(), e);
        }
        this.client = createdClient;
        this.qos = qosValue;
        this.retain = retainValue;
        this.messageIdPrefix = MqttProvider.TYPE + ":" + createdClient.getClientId() + ":";
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
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException e) {
            // 关闭阶段不因断连失败而上抛：客户端随之关闭即释放资源
        }
        try {
            client.close();
        } catch (MqttException e) {
            // 同上：关闭失败不改变「已关闭」这一对外可见状态
        }
        subscriptions.clear();
        registeredFilters.clear();
    }

    /**
     * 以配置的 {@code qos} / {@code retain} 发布一条消息的原始字节载荷，并返回本地生成的消息标识。
     *
     * <p>客户端 / 网络故障统一映射为 {@link MqException}；回填中断标志的语义由客户端承担（Paho 的发布
     * 方法不声明 {@code InterruptedException}）。</p>
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 发布失败
     */
    SendResult publish(Message message) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        try {
            client.publish(message.topic(), message.payload(), qos, retain);
            String messageId = messageIdPrefix + messageSeq.incrementAndGet();
            return SendResult.of(message.topic(), messageId);
        } catch (MqttException e) {
            throw new MqException("mqtt send failed, topic: " + message.topic(), e);
        } catch (MqException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MqException("mqtt send failed, topic: " + message.topic(), e);
        }
    }

    /**
     * 登记监听器：先做本地登记，再向客户端登记该 topic 过滤器（同过滤器只登记一次，避免重复派发）。
     *
     * @param topic 主题过滤器，支持 {@code +} / {@code #} 通配
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 向客户端登记订阅失败
     */
    void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
        if (registeredFilters.add(topic)) {
            try {
                client.subscribe(topic, qos, new MqttDeliveryListener(topic));
            } catch (MqttException e) {
                registeredFilters.remove(topic);
                throw new MqException("failed to subscribe mqtt topic: " + topic, e);
            }
        }
    }

    /**
     * 取消订阅登记：从该 topic 过滤器对应的登记列表中按<b>实例身份</b>移除该监听器实例（不使用 {@code equals}）。
     *
     * <p>属尽力语义：只解除本进程的本地绑定，不向 broker 发送取消订阅（同 topic 其它本地监听器的过滤器与
     * broker 侧订阅关系不受影响）；目标过滤器未登记或列表内无该实例时静默返回。</p>
     *
     * @param topic 主题过滤器
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
        throw new IllegalArgumentException("mqtt config value must be true or false: " + key);
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
            throw new IllegalArgumentException("mqtt config value must be an integer: " + key + "=" + value, e);
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
            throw new IllegalArgumentException("mqtt config value must not be negative: " + key + "=" + value);
        }
    }

    /**
     * 消费适配器：在客户端回调线程内把某个 topic 过滤器下的消息按本地登记顺序派发给监听器，并就地隔离监听器异常。
     */
    private final class MqttDeliveryListener implements IMqttMessageListener {

        /**
         * 本适配器负责派发的 topic 过滤器，同时是本地登记表的键。
         */
        private final String topicFilter;

        /**
         * 由总线在登记订阅时创建。
         *
         * @param topicFilter 该适配器对应的 topic 过滤器
         */
        MqttDeliveryListener(String topicFilter) {
            this.topicFilter = topicFilter;
        }

        /**
         * 收到一条消息：按本地登记顺序派发给该过滤器下的监听器，监听器异常就地隔离。
         *
         * @param topic 消息实际发布的主题
         * @param message 消息
         */
        @Override
        public void messageArrived(String topic, MqttMessage message) {
            List<MessageListener> listeners = subscriptions.get(topicFilter);
            if (listeners == null) {
                return;
            }
            Message received = Message.of(topic, message.getPayload());
            for (MessageListener listener : listeners) {
                try {
                    listener.onMessage(received);
                } catch (RuntimeException e) {
                    // 监听器异常就地隔离：不中断其余订阅者、不上抛给生产者
                }
            }
        }
    }
}
