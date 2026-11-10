/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.memory;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内发布-订阅总线：同步投递、单主题保序，是「监听器异常就地隔离」等共同底线的参考实现。
 *
 * <p>投递在调用线程内按登记顺序回调；取消按<b>实例身份</b>移除；关闭幂等，关闭后发送、订阅与取消均非法。
 * 各后端允许差异见 {@code docs/design.md} §5.6。</p>
 */
public class MemoryMessageBus implements MessageBus {

    private final Map<String, List<MessageListener>> subscriptions = new ConcurrentHashMap<>();

    /** 消息序号：messageId 即其自增后的十进制字符串（进程内单调递增）。 */
    private final AtomicLong messageSeq = new AtomicLong();

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

    /** {@inheritDoc} */
    @Override
    public void close() {
        closed = true;
        subscriptions.clear();
    }

    /**
     * 同步投递：按登记顺序回调，无订阅者时消息丢弃但仍成功（内存后端不做留存）。
     *
     * @param message 消息
     * @return 发送结果，messageId 为进程内递增序号
     * @throws IllegalStateException 总线已关闭
     */
    @Override
    public SendResult publish(Message message) {
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
    @Override
    public void subscribe(String topic, MessageListener listener) {
        if (closed) {
            throw new IllegalStateException("message bus has been closed");
        }
        subscriptions.computeIfAbsent(topic, key -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 按<b>实例身份</b>（引用相等，非 {@code equals}）移除该监听器；目标不存在时静默返回。
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
}
