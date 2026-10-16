/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MessageBus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.Test;

/**
 * {@link MessageBus} 的行为契约基类：由各后端实现继承运行，只覆盖跨后端的<b>共同底线</b>——生产订阅闭环、
 * 无订阅者时 {@code send} 仍成功返回 {@link SendResult}（§5.6 A5）、关闭语义、监听器异常隔离与订阅取消。
 *
 * <p>真实后端投递异步，故投递类断言一律经 {@link #awaitTrue(BooleanSupplier)} 等待条件成立（带超时），
 * 断言使用的收集容器为线程安全的 {@link CopyOnWriteArrayList}；同步后端（如内存后端）在同一钩子下首轮即
 * 通过。各后端的<b>差异行为</b>（投递模型、有序性、{@code messageId} 格式、投递的消息对象等）不属共同
 * 底线，放各后端子类的测试。</p>
 */
public abstract class MessageBusContractTest {

    /**
     * 轮询等待的默认上限（毫秒）。
     */
    private static final long DEFAULT_AWAIT_TIMEOUT_MILLIS = 10_000L;

    /**
     * 轮询间隔（毫秒）。
     */
    private static final long AWAIT_POLL_MILLIS = 10L;

    /**
     * 创建被测总线。
     *
     * @return 一个新的消息总线
     */
    protected abstract MessageBus newBus();

    /**
     * 等待上限钩子：默认 10 秒；真实后端子类可放宽（建连、rebalance 与消费线程调度更慢）。
     *
     * @return 等待上限（毫秒）
     */
    protected long awaitTimeoutMillis() {
        return DEFAULT_AWAIT_TIMEOUT_MILLIS;
    }

    /**
     * 主题命名钩子：默认恒等；真实后端子类可覆写为带随机前缀，以隔离 broker 侧主题、避免历史残留消息干扰。
     *
     * @param name 契约用例内部的主题名
     * @return 实际使用的主题名
     */
    protected String topic(String name) {
        return name;
    }

    /**
     * 以 {@link #awaitTimeoutMillis()} 为上限，轮询等待条件成立。
     *
     * @param condition 待成立的条件
     * @return 超时前条件成立返回 {@code true}
     */
    protected boolean awaitTrue(BooleanSupplier condition) {
        return awaitTrue(condition, awaitTimeoutMillis());
    }

    /**
     * 轮询等待条件成立；超时返回 {@code false}，超时前最后一次求值仍参与判定。
     *
     * @param condition 待成立的条件
     * @param timeoutMillis 超时（毫秒）
     * @return 超时前条件成立返回 {@code true}
     */
    protected static boolean awaitTrue(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (System.nanoTime() - deadline >= 0) {
                return condition.getAsBoolean();
            }
            try {
                Thread.sleep(AWAIT_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * 订阅后发送，订阅者收到同主题、同载荷的消息，并拿到带消息标识的发送结果。
     *
     * <p>投递强度只断言「<b>至少</b>收到该主题、该载荷的消息」——真实后端为 at-least-once、可能重复消费
     * （§5.6 B4），故此处不断言「恰好一次」；「恰好一次」的强度由内存后端（§5.6 B4 精确一次派发）另有断言。</p>
     */
    @Test
    public void shouldDeliverToSubscriber() {
        MessageBus bus = newBus();
        try {
            String topic = topic("orders");
            List<Message> received = new CopyOnWriteArrayList<>();
            bus.consumer().subscribe(topic, received::add);
            SendResult result = bus.producer().send(topic, "A1001");

            assertTrue("订阅者应在超时内至少收到该主题、该载荷的消息",
                    awaitTrue(() -> received.stream()
                            .anyMatch(message -> topic.equals(message.topic()) && "A1001".equals(text(message)))));
            assertEquals(topic, result.topic());
            assertNotNull(result.messageId());
        } finally {
            bus.close();
        }
    }

    /**
     * 无订阅者时 {@code send} 仍成功返回带 {@code messageId} 的 {@link SendResult}——属共同底线（§5.6 A5）。
     *
     * <p>本用例<b>只断言</b>上述「发送成功并返回带标识的结果」；而「消息被丢弃还是留存」随后端而变
     * （§5.6 B6：内存后端丢弃、broker 独立存储待消费者上线），<b>不属</b>共同底线，故此处不断言。</p>
     */
    @Test
    public void shouldSucceedWithoutSubscribers() {
        MessageBus bus = newBus();
        try {
            String topic = topic("none");
            SendResult result = bus.producer().send(topic, "x");

            assertEquals(topic, result.topic());
            assertNotNull(result.messageId());
        } finally {
            bus.close();
        }
    }

    /**
     * 关闭是幂等的：重复关闭不抛异常，且总线保持一致关闭状态（第二次关闭不会把状态翻回去）。
     */
    @Test
    public void shouldCloseIdempotently() {
        MessageBus bus = newBus();
        bus.close();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.producer().send(topic("t"), "x"));
    }

    /**
     * 关闭后发送抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectSendAfterClose() {
        MessageBus bus = newBus();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.producer().send(topic("t"), "x"));
    }

    /**
     * 关闭后订阅抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectSubscribeAfterClose() {
        MessageBus bus = newBus();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.consumer().subscribe(topic("t"), message -> {
        }));
    }

    /**
     * 单个监听器抛出的运行时异常就地隔离，不中断其余订阅者、不上抛给生产者。
     */
    @Test
    public void shouldIsolateListenerException() {
        MessageBus bus = newBus();
        try {
            String topic = topic("t");
            List<String> received = new CopyOnWriteArrayList<>();
            bus.consumer().subscribe(topic, message -> {
                throw new IllegalStateException("boom");
            });
            bus.consumer().subscribe(topic, message -> received.add("ok"));

            bus.producer().send(topic, "x");

            assertTrue("其余订阅者应在超时内至少收到该载荷的消息", awaitTrue(() -> received.contains("ok")));
        } finally {
            bus.close();
        }
    }

    /**
     * 订阅后取消该监听器，再发送，该监听器不再收到；同主题的其他监听器仍收到。
     */
    @Test
    public void shouldStopDeliveringAfterUnsubscribe() {
        MessageBus bus = newBus();
        try {
            String topic = topic("t");
            List<String> first = new CopyOnWriteArrayList<>();
            List<String> second = new CopyOnWriteArrayList<>();
            MessageListener firstListener = message -> first.add("hit");
            bus.consumer().subscribe(topic, firstListener);
            bus.consumer().subscribe(topic, message -> second.add("hit"));

            bus.consumer().unsubscribe(topic, firstListener);
            bus.producer().send(topic, "x");

            assertTrue("未取消的订阅者应在超时内至少收到该载荷的消息", awaitTrue(() -> second.contains("hit")));
            assertTrue("已取消的监听器不应再收到消息", first.isEmpty());
        } finally {
            bus.close();
        }
    }

    /**
     * 重复取消、取消从未登记过的订阅均为幂等静默 no-op（不抛异常）。
     */
    @Test
    public void shouldUnsubscribeIdempotently() {
        MessageBus bus = newBus();
        try {
            String topic = topic("t");
            MessageListener listener = message -> {
            };
            bus.consumer().subscribe(topic, listener);

            // 任一调用若抛异常，用例即失败
            bus.consumer().unsubscribe(topic, listener);
            bus.consumer().unsubscribe(topic, listener);
            bus.consumer().unsubscribe(topic, message -> {
            });
            bus.consumer().unsubscribe(topic("never"), listener);
        } finally {
            bus.close();
        }
    }

    /**
     * 关闭后取消抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectUnsubscribeAfterClose() {
        MessageBus bus = newBus();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.consumer().unsubscribe(topic("t"), message -> {
        }));
    }

    /**
     * 取消按<b>实例身份</b>（引用相等）移除，而非按 {@code equals}：两个 {@code equals} 判定相等、但并非同一
     * 实例的监听器，取消其一后仅该实例被移除，另一个仍能收到消息。
     *
     * <p>本用例锁定契约的失效边界——它针对「按 {@code equals} 移除」的实现退化：① 若实现误改为
     * {@code removeIf(e -> e.equals(listener))}（按值语义移除全部相等元素），A、B 会被一并移除，断言失败；
     * ② 若实现误改为 {@code list.remove(listener)}（移除首个 {@code equals} 相等的元素），则会移除先登记的
     * B、而留下 A，断言同样失败。为同时覆盖 ① ②，这里刻意<b>先登记 B、再登记 A</b>，再取消 A。</p>
     */
    @Test
    public void shouldRemoveByInstanceIdentityNotEquals() {
        MessageBus bus = newBus();
        try {
            String topic = topic("t");
            List<String> received = new CopyOnWriteArrayList<>();
            MessageListener listenerA = new EqualsEquivalentListener(received, "A");
            MessageListener listenerB = new EqualsEquivalentListener(received, "B");

            // 前置事实：两实例 equals 判等，但并非同一对象
            assertEquals(listenerA, listenerB);
            assertNotSame(listenerA, listenerB);

            // 先登记 B、再登记 A，使「按 equals 移除首个」的实现也会移除 B（而非 A）
            bus.consumer().subscribe(topic, listenerB);
            bus.consumer().subscribe(topic, listenerA);

            bus.consumer().unsubscribe(topic, listenerA);
            bus.producer().send(topic, "x");

            // 仅 A 被移除、B 仍在；按 equals 移除时两者皆无，此断言即失败
            assertTrue("未被取消的实例应在超时内至少收到消息", awaitTrue(() -> received.contains("B")));
            assertTrue("被取消的实例不应再收到消息", !received.contains("A"));
        } finally {
            bus.close();
        }
    }

    /**
     * 以 UTF-8 解码消息载荷。
     *
     * @param message 消息
     * @return 文本载荷
     */
    protected static String text(Message message) {
        return new String(message.payload(), StandardCharsets.UTF_8);
    }

    /**
     * 用于锁定「按实例身份移除」的监听器：同类型任意实例之间 {@code equals} 判等、{@code hashCode} 一致，
     * 但不同实例并非同一对象——据此即可区分「按 {@code ==} 移除」与「按 {@code equals} 移除」。
     *
     * <p>仅供 {@link #shouldRemoveByInstanceIdentityNotEquals()} 使用，不影响其它用例。</p>
     */
    private static final class EqualsEquivalentListener implements MessageListener {

        /**
         * 收到消息时追加自身标签的记录列表。
         */
        private final List<String> received;

        /**
         * 实例标签，用于断言「哪个实例收到了消息」。
         */
        private final String tag;

        /**
         * 创建监听器。
         *
         * @param received 记录列表
         * @param tag 实例标签
         */
        EqualsEquivalentListener(List<String> received, String tag) {
            this.received = received;
            this.tag = tag;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onMessage(Message message) {
            received.add(tag);
        }

        /**
         * 同类型实例一律判等：模拟函数式接口实例「{@code equals} 无可靠值语义」的处境。
         *
         * @param other 另一对象
         * @return 是否为同类监听器实例
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof EqualsEquivalentListener;
        }

        /**
         * 与 {@link #equals(Object)} 保持一致：同类实例哈希相同。
         *
         * @return 固定哈希值
         */
        @Override
        public int hashCode() {
            return EqualsEquivalentListener.class.hashCode();
        }
    }
}
