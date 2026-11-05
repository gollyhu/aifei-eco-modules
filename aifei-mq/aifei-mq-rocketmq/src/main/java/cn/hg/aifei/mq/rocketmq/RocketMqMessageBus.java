/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.rocketmq;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.MqException;
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
 * RocketMQ 后端总线：生产者同步发送到 broker，消费者在 RocketMQ 自有线程内按本地登记顺序派发；messageId 取
 * broker 返回的标识，消费位点与消费组由 broker 维护。
 *
 * <p><b>懒连接</b>：构造期只启动生产者，且只对<b>配置</b>错误（缺 {@code namesrv-addr}）立即失败；{@code start()}
 * 不校验 nameserver 连通性，故 nameserver 不可达时构造仍成功——「启动成功」≠「后端可达」，故障延迟到首次
 * {@code send} / 拉取才以 {@link MqException} 暴露。消费者因订阅主题启动时未知，延迟到<b>首次订阅</b>才订阅并
 * 启动。{@code unsubscribe} 只解除本地绑定，不调 broker 侧 {@code unsubscribe}，以免影响同主题其它监听器与
 * 消费组一致性。各后端共同底线与允许差异见 {@code docs/design.md} §5.6。</p>
 */
public class RocketMqMessageBus implements MessageBus {

    /** nameserver 地址配置键：形如 {@code host:port}，多个以 {@code ;} 分隔。 */
    private static final String KEY_NAMESRV_ADDR = "namesrv-addr";

    /** 生产者组配置键。 */
    private static final String KEY_PRODUCER_GROUP = "producer-group";

    /** 消费者组配置键。 */
    private static final String KEY_CONSUMER_GROUP = "consumer-group";

    // 未配置组名时用的默认值，与上方键名逐一对应；标签表达式恒订阅全部标签
    private static final String DEFAULT_PRODUCER_GROUP = "DEFAULT_PRODUCER";

    private static final String DEFAULT_CONSUMER_GROUP = "DEFAULT_CONSUMER";

    private static final String DEFAULT_TAG_EXPRESSION = "*";

    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /** 已向消费者登记订阅的主题；由 {@link #consumerLock} 保护。 */
    private final Set<String> subscribedTopics = new HashSet<>();

    private final DefaultMQProducer producerClient;

    /** 首次订阅时才启动。 */
    private final DefaultMQPushConsumer consumerClient;

    /** 保护消费者的订阅登记与启动。 */
    private final Object consumerLock = new Object();

    private boolean consumerStarted;

    private volatile boolean closed;

    /**
     * 读取配置并创建客户端：生产者立即启动，消费者待首次订阅时启动。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     * @throws MqException 未配置 nameserver 地址，或生产者启动失败（配置层面；不因 nameserver 不可达而抛）
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

    /** {@inheritDoc} */
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
     * 同步发送到 broker；非 {@code SEND_OK}、客户端 / 网络故障与中断（回填中断标志）统一映射为
     * {@link MqException}。
     *
     * @param message 消息
     * @return 发送结果，messageId 为 broker 返回的标识
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 发送失败或 broker 未返回消息标识
     */
    @Override
    public SendResult publish(Message message) {
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
     * 登记监听器：首次调用为消费者订阅主题并启动，此后仅追加本地登记。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     * @throws MqException 消费者订阅主题或启动失败
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
     * 按<b>实例身份</b>移除该监听器，只解除本进程的本地绑定（尽力语义）；目标不存在时静默返回。
     *
     * @param topic 主题
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

    /** 读取后端专有配置：未配置或只含空白时取默认值。 */
    private static String group(MqConfig config, String key, String defaultValue) {
        String value = config.get(key);
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    /**
     * 消费适配器：恒返回消费成功，不触发 broker 侧重试，使监听器异常隔离语义与共同底线一致。
     */
    private final class RocketMqDeliveryListener implements MessageListenerConcurrently {

        /**
         * 按本地登记顺序派发一批消息，监听器异常就地隔离。
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
