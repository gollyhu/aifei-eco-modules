/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内发布-订阅总线：同步投递、单主题保序、订阅登记与取消、关闭语义的唯一实现。
 *
 * <p>投递在调用线程内按订阅登记顺序逐个同步回调；单个监听器抛出的运行时异常就地隔离，不中断其余订阅者、
 * 也不上抛给生产者。取消按实例身份移除该监听器实例。关闭幂等，关闭后发送、订阅与取消均视为状态非法。</p>
 */
public class MemoryMessageBus implements MessageBus {

    /**
     * 主题到监听器的订阅登记；键集合即已订阅主题。
     */
    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 消息序号，用于生成进程内全局单调递增的消息标识。
     */
    private final AtomicLong messageSeq = new AtomicLong();

    /**
     * 生产者视图；构造时创建，与消费者视图共用本总线。
     */
    private final Producer producer = new MemoryProducer(this);

    /**
     * 消费者视图；构造时创建，与生产者视图共用本总线。
     */
    private final Consumer consumer = new MemoryConsumer(this);

    /**
     * 是否已关闭。
     */
    private volatile boolean closed;

    /**
     * 创建总线；内存后端无额外配置，配置仅作非空校验。
     *
     * @param config 配置对象
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     */
    public MemoryMessageBus(MqConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config can not be null");
        }
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
        closed = true;
        subscriptions.clear();
    }

    /**
     * 投递一条消息：分配消息标识后按订阅登记顺序同步回调，无订阅者时消息丢弃但仍成功。
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalStateException 总线已关闭
     */
    SendResult publish(Message message) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        String messageId = Long.toString(messageSeq.incrementAndGet());
        List<MessageListener> listeners = subscriptions.get(message.topic());
        if (listeners != null) {
            for (MessageListener listener : listeners) {
                try {
                    listener.onMessage(message);
                } catch (RuntimeException e) {
                    // 监听器异常就地隔离：不中断其余订阅者、不上抛给生产者
                }
            }
        }
        return SendResult.of(message.topic(), messageId);
    }

    /**
     * 追加订阅登记。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalStateException 总线已关闭
     */
    void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 取消订阅登记：从该主题的登记列表中按<b>实例身份</b>移除该监听器实例。
     *
     * <p>按引用相等判定（{@code e == listener}），不使用 {@code equals}；目标主题未登记或列表内无该实例时静默返回。
     * 移除后保留该主题的空列表（不移除主题键）。</p>
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
}
