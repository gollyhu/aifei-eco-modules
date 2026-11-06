/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.mqtt5;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

/**
 * MQTT v5.0 后端总线（实验性，未真机验证）：生产与消费共用<b>单一客户端</b>（一条连接），构造内即建连。
 *
 * <p>与 {@code mqtt}（v3.1.1）<b>同构</b>：构造内建连 <b>fail-fast</b>、回调在客户端线程、{@code unsubscribe}
 * 仅本地尽力语义；但命名空间互不相通，且 v5 <b>无</b> {@code clean-session}，会话语义改由 {@code clean-start}
 * 与 {@code session-expiry-interval-seconds}（秒）表达。{@code topic} 是 MQTT <b>过滤器</b>，共享订阅
 * {@code $share/<group>/<topic>} 也由调用方在 topic 内表达。</p>
 *
 * <p>broker 不回标识，故 {@code messageId} 由适配器<b>本地生成</b>，格式 {@code mqtt5:<生效 client-id>:<序号>}；
 * <b>不得</b>以 QoS packet identifier 充当 {@code messageId}。投递保证取决于 {@code qos}（0 = at-most-once，
 * 1/2 = at-least-once），重投下监听器需幂等；重连走客户端原生退避（起始 / 上限均可配）。共同底线与允许差异见
 * {@code docs/design.md} §5.6。</p>
 */
public class Mqtt5MessageBus implements MessageBus {

    /** broker 地址配置键：客户端口径 server URI，形如 {@code tcp://host:1883}。 */
    private static final String KEY_BROKER_URL = "broker-url";

    /** 客户端标识配置键；未配置时由本适配器生成非空标识。 */
    private static final String KEY_CLIENT_ID = "client-id";

    /** v5 clean start 标志配置键（替代 v3 的 {@code clean-session}）。 */
    private static final String KEY_CLEAN_START = "clean-start";

    /** v5 会话过期时间（秒）配置键。 */
    private static final String KEY_SESSION_EXPIRY_INTERVAL_SECONDS = "session-expiry-interval-seconds";

    /** 订阅与发布的默认 QoS 配置键。 */
    private static final String KEY_QOS = "qos";

    /** 发布是否设置 retain 标志的配置键。 */
    private static final String KEY_RETAIN = "retain";

    /** 认证用户名配置键；只进配置域。 */
    private static final String KEY_USERNAME = "username";

    /** 认证口令配置键；只进配置域。 */
    private static final String KEY_PASSWORD = "password";

    /** 心跳间隔（秒）配置键。 */
    private static final String KEY_KEEP_ALIVE_SECONDS = "keep-alive-seconds";

    /** 连接超时（秒）配置键。 */
    private static final String KEY_CONNECTION_TIMEOUT_SECONDS = "connection-timeout-seconds";

    /** 是否启用客户端原生自动重连的配置键。 */
    private static final String KEY_AUTO_RECONNECT = "auto-reconnect";

    /** 重连退避<b>起始</b>间隔（毫秒）配置键。 */
    private static final String KEY_RECONNECT_DELAY_MILLIS = "reconnect-delay-millis";

    /** 重连退避<b>上限</b>（毫秒）配置键。 */
    private static final String KEY_RECONNECT_DELAY_MAX_MILLIS = "reconnect-delay-max-millis";

    // 默认值与上方键名逐一对应（clean-start / session-expiry-interval-seconds / qos / retain /
    // keep-alive-seconds / connection-timeout-seconds / auto-reconnect / reconnect-delay-millis /
    // reconnect-delay-max-millis）；会话过期 0 表示会话不跨连接保留
    private static final boolean DEFAULT_CLEAN_START = true;

    private static final int DEFAULT_SESSION_EXPIRY_INTERVAL_SECONDS = 0;

    private static final int DEFAULT_QOS = 0;

    private static final boolean DEFAULT_RETAIN = false;

    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    private static final int DEFAULT_CONNECTION_TIMEOUT_SECONDS = 30;

    private static final boolean DEFAULT_AUTO_RECONNECT = true;

    private static final int DEFAULT_RECONNECT_DELAY_MILLIS = 1000;

    private static final int DEFAULT_RECONNECT_DELAY_MAX_MILLIS = 30000;

    // QoS 合法区间
    private static final int MIN_QOS = 0;

    private static final int MAX_QOS = 2;

    /**
     * 未配置客户端标识时本适配器生成标识所用的前缀。
     */
    private static final String GENERATED_CLIENT_ID_PREFIX = "aifei-mq-";

    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /** 已向客户端登记订阅的过滤器：同一过滤器只登记一次，避免重复派发。 */
    private final Set<String> registeredFilters = ConcurrentHashMap.newKeySet();

    private final AtomicLong messageSeq = new AtomicLong();

    /** 生产与消费共用，构造内已建连。 */
    private final MqttClient client;

    private final int qos;

    private final boolean retain;

    /** 本地 messageId 前缀，形如 {@code mqtt5:<生效 client-id>:}。 */
    private final String messageIdPrefix;

    private volatile boolean closed;

    /**
     * 读取配置、创建唯一客户端并<b>立即建连</b>：配置错误与 broker 不可达都在构造期失败，不留半装配。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}，或数值键取值非法
     * @throws MqException 未配置 broker 地址，或客户端创建 / 建连失败（broker 不可达）
     */
    public Mqtt5MessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String brokerUrl = textValue(config, KEY_BROKER_URL);
        if (brokerUrl == null) {
            throw new MqException("mqtt5 broker url is required: "
                    + config.prefix() + "." + config.type() + "." + KEY_BROKER_URL);
        }
        String clientId = textValue(config, KEY_CLIENT_ID);
        boolean cleanStart = booleanValue(config, KEY_CLEAN_START, DEFAULT_CLEAN_START);
        int sessionExpiryIntervalSeconds = intValue(config, KEY_SESSION_EXPIRY_INTERVAL_SECONDS,
                DEFAULT_SESSION_EXPIRY_INTERVAL_SECONDS);
        int qosValue = intValue(config, KEY_QOS, DEFAULT_QOS);
        if (qosValue < MIN_QOS || qosValue > MAX_QOS) {
            throw new IllegalArgumentException("mqtt5 config value must be " + MIN_QOS + ", 1 or " + MAX_QOS
                    + ": " + KEY_QOS + "=" + qosValue);
        }
        boolean retainValue = booleanValue(config, KEY_RETAIN, DEFAULT_RETAIN);
        String username = textValue(config, KEY_USERNAME);
        String password = config.get(KEY_PASSWORD);
        int keepAliveSeconds = intValue(config, KEY_KEEP_ALIVE_SECONDS, DEFAULT_KEEP_ALIVE_SECONDS);
        int connectionTimeoutSeconds = intValue(config, KEY_CONNECTION_TIMEOUT_SECONDS,
                DEFAULT_CONNECTION_TIMEOUT_SECONDS);
        boolean autoReconnect = booleanValue(config, KEY_AUTO_RECONNECT, DEFAULT_AUTO_RECONNECT);
        int reconnectDelayMillis = intValue(config, KEY_RECONNECT_DELAY_MILLIS, DEFAULT_RECONNECT_DELAY_MILLIS);
        int reconnectDelayMaxMillis = intValue(config, KEY_RECONNECT_DELAY_MAX_MILLIS,
                DEFAULT_RECONNECT_DELAY_MAX_MILLIS);
        requireNonNegative(KEY_SESSION_EXPIRY_INTERVAL_SECONDS, sessionExpiryIntervalSeconds);
        requireNonNegative(KEY_KEEP_ALIVE_SECONDS, keepAliveSeconds);
        requireNonNegative(KEY_CONNECTION_TIMEOUT_SECONDS, connectionTimeoutSeconds);
        requireNonNegative(KEY_RECONNECT_DELAY_MILLIS, reconnectDelayMillis);
        requireNonNegative(KEY_RECONNECT_DELAY_MAX_MILLIS, reconnectDelayMaxMillis);

        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(cleanStart);
        options.setSessionExpiryInterval(Long.valueOf(sessionExpiryIntervalSeconds));
        options.setKeepAliveInterval(keepAliveSeconds);
        options.setConnectionTimeout(connectionTimeoutSeconds);
        options.setAutomaticReconnect(autoReconnect);
        if (autoReconnect) {
            options.setAutomaticReconnectDelay(reconnectDelayMillis, reconnectDelayMaxMillis);
        }
        if (username != null) {
            options.setUserName(username);
        }
        if (password != null && !password.isEmpty()) {
            options.setPassword(password.getBytes(StandardCharsets.UTF_8));
        }

        MqttClient createdClient;
        try {
            String effectiveClientId = (clientId == null) ? generateClientId() : clientId;
            createdClient = new MqttClient(brokerUrl, effectiveClientId);
            createdClient.connect(options);
        } catch (MqttException e) {
            throw new MqException("failed to connect mqtt5 broker: " + e.getMessage(), e);
        }
        this.client = createdClient;
        this.qos = qosValue;
        this.retain = retainValue;
        this.messageIdPrefix = Mqtt5Provider.TYPE + ":" + createdClient.getClientId() + ":";
    }

    /** {@inheritDoc} */
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
     * 以配置的 {@code qos} / {@code retain} 发布原始字节；客户端 / 网络故障统一映射为 {@link MqException}。
     *
     * @param message 消息
     * @return 发送结果，messageId 为本地生成的 {@code mqtt5:<client-id>:<序号>}
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 发布失败
     */
    @Override
    public SendResult publish(Message message) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        try {
            client.publish(message.topic(), message.payload(), qos, retain);
            String messageId = messageIdPrefix + messageSeq.incrementAndGet();
            return SendResult.of(message.topic(), messageId);
        } catch (MqttException e) {
            throw new MqException("mqtt5 send failed, topic: " + message.topic(), e);
        } catch (MqException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MqException("mqtt5 send failed, topic: " + message.topic(), e);
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
    @Override
    public void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
        if (registeredFilters.add(topic)) {
            try {
                client.subscribe(new MqttSubscription[] {new MqttSubscription(topic, qos)},
                        new IMqttMessageListener[] {new Mqtt5DeliveryListener(topic)});
            } catch (MqttException e) {
                registeredFilters.remove(topic);
                throw new MqException("failed to subscribe mqtt5 topic: " + topic, e);
            }
        }
    }

    /**
     * 按<b>实例身份</b>移除该监听器，只解除本进程本地绑定（尽力语义，不向 broker 发送取消订阅）；
     * 目标不存在时静默返回。
     *
     * @param topic 主题过滤器
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
     * 生成非空客户端标识：v5 客户端无 v3 的 {@code generateClientId()} 入口，空标识会让本地 messageId 的标识段落空。
     *
     * @return 非空客户端标识
     */
    private static String generateClientId() {
        return GENERATED_CLIENT_ID_PREFIX + System.nanoTime();
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
        throw new IllegalArgumentException("mqtt5 config value must be true or false: " + key);
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
            throw new IllegalArgumentException("mqtt5 config value must be an integer: " + key + "=" + value, e);
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
            throw new IllegalArgumentException("mqtt5 config value must not be negative: " + key + "=" + value);
        }
    }

    /**
     * 消费适配器：在客户端回调线程内按本地登记顺序派发某个过滤器下的消息，监听器异常就地隔离。
     */
    private final class Mqtt5DeliveryListener implements IMqttMessageListener {

        /** 负责派发的 topic 过滤器，同时是本地登记表的键。 */
        private final String topicFilter;

        /**
         * @param topicFilter 该适配器对应的 topic 过滤器
         */
        Mqtt5DeliveryListener(String topicFilter) {
            this.topicFilter = topicFilter;
        }

        /**
         * 按本地登记顺序派发给该过滤器下的监听器，监听器异常就地隔离。
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
