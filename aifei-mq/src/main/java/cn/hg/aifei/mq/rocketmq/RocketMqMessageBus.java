/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rocketmq;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * RocketMQ 后端总线：生产者同步发送到 broker，消费者在 RocketMQ 自有线程内按本地登记顺序派发。
 *
 * <p>启动时机：构造内创建并启动生产者客户端；消费者因订阅主题在启动时未知，延迟到<b>首次订阅</b>时才订阅主题
 * 并启动。构造期只对<b>配置</b>错误（缺 {@code namesrv-addr}）立即失败、不留半装配；{@code start()} <b>不校验
 * nameserver 连通性</b>（懒连接），故 nameserver 不可达时构造仍成功，「启动成功」≠「后端可达」，这类故障通常
 * 延迟到首次 {@code send} / 消费拉取才以 {@link MqException} 暴露。消费者侧的消费位点与消费组由 broker 维护。</p>
 *
 * <p>投递与消费：{@code publish} 同步调用生产者的 {@code send}，{@code messageId} 取 broker 返回的消息标识；
 * 消费者按<b>本地登记顺序</b>回调监听器，单个监听器抛出的运行时异常就地隔离（不中断其余订阅者、不上抛）。
 * {@code unsubscribe} 只解除本地绑定（尽力语义），不调用 broker 侧 {@code unsubscribe}，以免影响同主题其它
 * 本地监听器与消费组一致性。</p>
 *
 * <p>关闭幂等：关闭后发送、订阅与取消均视为状态非法。</p>
 */
public class RocketMqMessageBus implements MessageBus {

    /**
     * nameserver 地址配置键，形如 {@code host:port}，多个以 {@code ;} 分隔。
     */
    private static final String KEY_NAMESRV_ADDR = "namesrv-addr";

    /**
     * 生产者组配置键。
     */
    private static final String KEY_PRODUCER_GROUP = "producer-group";

    /**
     * 消费者组配置键。
     */
    private static final String KEY_CONSUMER_GROUP = "consumer-group";

    /**
     * 未配置生产者组时使用的组名。
     */
    private static final String DEFAULT_PRODUCER_GROUP = "DEFAULT_PRODUCER";

    /**
     * 未配置消费者组时使用的组名。
     */
    private static final String DEFAULT_CONSUMER_GROUP = "DEFAULT_CONSUMER";

    /**
     * 订阅全部标签。
     */
    private static final String DEFAULT_TAG_EXPRESSION = "*";

    /**
     * 主题到本地监听器的订阅登记；键集合即已登记主题。
     */
    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 已向消费者登记订阅的主题；由 {@link #consumerLock} 保护。
     */
    private final Set<String> subscribedTopics = new HashSet<>();

    /**
     * 生产者视图；构造时创建，与消费者视图共用本总线。
     */
    private final Producer producer = new RocketMqProducer(this);

    /**
     * 消费者视图；构造时创建，与生产者视图共用本总线。
     */
    private final Consumer consumer = new RocketMqConsumer(this);

    /**
     * RocketMQ 生产者客户端，构造内已启动。
     */
    private final DefaultMQProducer producerClient;

    /**
     * RocketMQ 消费者客户端，首次订阅时启动。
     */
    private final DefaultMQPushConsumer consumerClient;

    /**
     * 保护消费者客户端的订阅登记与启动。
     */
    private final Object consumerLock = new Object();

    /**
     * 消费者客户端是否已启动。
     */
    private boolean consumerStarted;

    /**
     * 是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 读取配置并创建客户端：生产者立即启动，消费者待首次订阅时启动；生产者启动<b>不校验</b> nameserver 连通性
     * （懒连接），连通性故障延迟到首次使用才暴露。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     * @throws MqException 未配置 nameserver 地址，或生产者客户端启动失败（配置层面；不因 nameserver 不可达而抛）
     */
    public RocketMqMessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
        String namesrvAddr = config.get(KEY_NAMESRV_ADDR);
        if (namesrvAddr == null || namesrvAddr.trim().isEmpty()) {
            throw new MqException("rocketmq nameserver address is required: "
                    + config.prefix() + "." + config.type() + "." + KEY_NAMESRV_ADDR);
        }
        String address = namesrvAddr.trim();

        DefaultMQProducer createdProducer = new DefaultMQProducer(group(config, KEY_PRODUCER_GROUP,
                DEFAULT_PRODUCER_GROUP));
        createdProducer.setNamesrvAddr(address);
        try {
            createdProducer.start();
        } catch (MQClientException e) {
            throw new MqException("failed to start rocketmq producer: " + e.getMessage(), e);
        }
        this.producerClient = createdProducer;

        DefaultMQPushConsumer createdConsumer = new DefaultMQPushConsumer(group(config, KEY_CONSUMER_GROUP,
                DEFAULT_CONSUMER_GROUP));
        createdConsumer.setNamesrvAddr(address);
        // 新消费组从队列起始位点消费，与「无在线消费者时消息留存、消费者上线后消费」的后端语义一致
        createdConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        createdConsumer.registerMessageListener(new RocketMqDeliveryListener());
        this.consumerClient = createdConsumer;
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
        producerClient.shutdown();
        synchronized (consumerLock) {
            consumerClient.shutdown();
            consumerStarted = false;
            subscribedTopics.clear();
        }
        subscriptions.clear();
    }

    /**
     * 同步发送一条消息到 broker，并返回 broker 分配的消息标识。
     *
     * <p>非 {@code SEND_OK} 与客户端 / 网络故障统一映射为 {@link MqException}；中断需回填中断标志。</p>
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
        org.apache.rocketmq.common.message.Message rocketMessage =
                new org.apache.rocketmq.common.message.Message(message.topic(), message.payload());
        try {
            org.apache.rocketmq.client.producer.SendResult sent = producerClient.send(rocketMessage);
            if (sent == null || sent.getSendStatus() != SendStatus.SEND_OK) {
                throw new MqException("rocketmq send failed, topic: " + message.topic()
                        + ", status: " + (sent == null ? "null" : sent.getSendStatus()));
            }
            String messageId = sent.getMsgId();
            if (messageId == null) {
                // broker 未返回消息标识属运行时故障，而非参数非法
                throw new MqException("rocketmq send returned no message id, topic: " + message.topic());
            }
            return SendResult.of(message.topic(), messageId);
        } catch (MQClientException | MQBrokerException | RemotingException e) {
            throw new MqException("rocketmq send failed, topic: " + message.topic(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqException("rocketmq send interrupted, topic: " + message.topic(), e);
        } catch (MqException e) {
            throw e;
        } catch (RuntimeException e) {
            // 客户端还会抛出未在 throws 中声明的故障（如 nameserver 不可达时路由更新抛 IllegalStateException），
            // 同属运行时故障，统一归 MqException
            throw new MqException("rocketmq send failed, topic: " + message.topic(), e);
        }
    }

    /**
     * 登记监听器并确保消费者已就绪：首次调用为消费者订阅主题并启动，此后仅追加本地登记。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 消费者订阅主题或启动失败
     */
    void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        synchronized (consumerLock) {
            if (closed) {
                throw new IllegalStateException("message bus has been closed");
            }
            if (subscribedTopics.add(topic)) {
                try {
                    consumerClient.subscribe(topic, DEFAULT_TAG_EXPRESSION);
                } catch (MQClientException | RuntimeException e) {
                    subscribedTopics.remove(topic);
                    throw new MqException("failed to subscribe rocketmq topic: " + topic, e);
                }
            }
            subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
            if (!consumerStarted) {
                try {
                    consumerClient.start();
                    consumerStarted = true;
                } catch (MQClientException | RuntimeException e) {
                    throw new MqException("failed to start rocketmq consumer: " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 取消订阅登记：从该主题的登记列表中按<b>实例身份</b>移除该监听器实例（不使用 {@code equals}）。
     *
     * <p>属尽力语义：只解除本进程的本地绑定，不调用 broker 侧取消订阅，故 broker 侧订阅关系与已拉取批次
     * 不受影响；目标主题未登记或列表内无该实例时静默返回。</p>
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
     * 读取后端专有配置：未配置或只含空白时取默认值。
     *
     * @param config 配置对象
     * @param key 相对键名
     * @param defaultValue 默认值
     * @return 配置值或默认值
     */
    private static String group(MqConfig config, String key, String defaultValue) {
        String value = config.get(key);
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    /**
     * 消费适配器：在 RocketMQ 消费线程内把消息按本地登记顺序派发给监听器，并就地隔离监听器异常。
     *
     * <p>恒返回消费成功，不触发 broker 侧重试，从而使「监听器异常不中断其余订阅者、不上抛」的隔离语义
     * 与共同底线一致。</p>
     */
    private final class RocketMqDeliveryListener implements MessageListenerConcurrently {

        /**
         * 消费一批消息：按本地登记顺序派发给监听器，监听器异常就地隔离。
         *
         * @param messages 本次拉取的消息批次
         * @param context 消费上下文
         * @return 恒为 {@link ConsumeConcurrentlyStatus#CONSUME_SUCCESS}，不触发 broker 侧重试
         */
        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages,
                ConsumeConcurrentlyContext context) {
            for (MessageExt ext : messages) {
                Message message = Message.of(ext.getTopic(), ext.getBody());
                List<MessageListener> listeners = subscriptions.get(ext.getTopic());
                if (listeners == null) {
                    continue;
                }
                for (MessageListener listener : listeners) {
                    try {
                        listener.onMessage(message);
                    } catch (RuntimeException e) {
                        // 监听器异常就地隔离：不中断其余订阅者、不上抛给生产者
                    }
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }
}
