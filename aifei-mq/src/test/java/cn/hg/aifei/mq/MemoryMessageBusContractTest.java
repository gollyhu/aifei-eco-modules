/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MessageBus;
import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqProviderRegistry;
import cn.hg.aifei.mq.memory.MemoryMessageBus;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;

/**
 * 内存后端继承运行 {@link MessageBusContractTest} 的全部契约，并承载内存后端的<b>专有行为</b>用例。
 *
 * <p>专有用例中的 4 项（单主题 FIFO、多订阅者按登记顺序、{@code messageId} 十进制递增、投递同一消息对象）
 * 属「允许后端差异」而非共同底线，故由基类移入此处；其余为内存实现的细节用例。</p>
 */
public class MemoryMessageBusContractTest extends MessageBusContractTest {

    /**
     * {@inheritDoc}
     */
    @Override
    protected MessageBus newBus() {
        return newMemoryBus();
    }

    /**
     * 同一主题内按发送完成顺序投递（单主题 FIFO）。
     */
    @Test
    public void shouldPreserveSingleTopicOrder() {
        MessageBus bus = newBus();
        try {
            List<String> received = new ArrayList<>();
            bus.consumer().subscribe("t", message -> received.add(text(message)));
            bus.producer().send("t", "1");
            bus.producer().send("t", "2");
            bus.producer().send("t", "3");

            assertEquals(Arrays.asList("1", "2", "3"), received);
        } finally {
            bus.close();
        }
    }

    /**
     * 同一主题的多个订阅者按登记顺序回调。
     */
    @Test
    public void shouldInvokeSubscribersInRegistrationOrder() {
        MessageBus bus = newBus();
        try {
            List<String> order = new ArrayList<>();
            bus.consumer().subscribe("t", message -> order.add("first"));
            bus.consumer().subscribe("t", message -> order.add("second"));
            bus.producer().send("t", "x");

            assertEquals(Arrays.asList("first", "second"), order);
        } finally {
            bus.close();
        }
    }

    /**
     * 消息标识在同一总线上单调递增。
     */
    @Test
    public void shouldAssignIncreasingMessageId() {
        MessageBus bus = newBus();
        try {
            String first = bus.producer().send("t", "1").messageId();
            String second = bus.producer().send("t", "2").messageId();

            assertTrue(Long.parseLong(second) > Long.parseLong(first));
        } finally {
            bus.close();
        }
    }

    /**
     * 投递给订阅者的是发送时构造的同一消息对象。
     */
    @Test
    public void shouldDeliverSameMessageInstance() {
        MessageBus bus = newBus();
        try {
            Message message = Message.of("t", new byte[] {1, 2, 3});
            List<Message> received = new ArrayList<>();
            bus.consumer().subscribe("t", received::add);
            bus.producer().send(message);

            assertEquals(Collections.singletonList(message), received);
        } finally {
            bus.close();
        }
    }

    /**
     * 内存后端为进程内同步、<b>恰好一次</b>派发（§5.6 B4）：一次 {@code send} 只回调订阅者一次，且主题与
     * 载荷正确——即契约基类放宽为「至少收到」的基本投递闭环，在内存后端仍保持「恰好一次」强度。
     */
    @Test
    public void shouldDeliverToSubscriberExactlyOnce() {
        MessageBus bus = newBus();
        try {
            List<Message> received = new ArrayList<>();
            bus.consumer().subscribe("t", received::add);
            bus.producer().send("t", "A1001");

            assertEquals(1, received.size());
            assertEquals("t", received.get(0).topic());
            assertEquals("A1001", text(received.get(0)));
        } finally {
            bus.close();
        }
    }

    /**
     * 监听器异常隔离后，其余订阅者在内存后端仍<b>恰好一次</b>收到该载荷（§5.6 A4 + B4）：契约基类放宽为
     * 「至少收到」，内存后端补回「恰好一次」强度。
     */
    @Test
    public void shouldIsolateListenerExceptionExactlyOnce() {
        MessageBus bus = newBus();
        try {
            List<String> received = new ArrayList<>();
            bus.consumer().subscribe("t", message -> {
                throw new IllegalStateException("boom");
            });
            bus.consumer().subscribe("t", message -> received.add("ok"));

            bus.producer().send("t", "x");

            assertEquals(Collections.singletonList("ok"), received);
        } finally {
            bus.close();
        }
    }

    /**
     * 取消后，已取消的监听器在内存后端<b>恰好不再收到</b>、其余监听器仍<b>恰好一次</b>收到该载荷
     * （§5.6 A3 取消语义 + B4 精确一次）。
     */
    @Test
    public void shouldStopDeliveringExactlyOnceAfterUnsubscribe() {
        MessageBus bus = newBus();
        try {
            List<String> first = new ArrayList<>();
            List<String> second = new ArrayList<>();
            MessageListener firstListener = message -> first.add("hit");
            bus.consumer().subscribe("t", firstListener);
            bus.consumer().subscribe("t", message -> second.add("hit"));

            bus.consumer().unsubscribe("t", firstListener);
            bus.producer().send("t", "x");

            assertTrue(first.isEmpty());
            assertEquals(Collections.singletonList("hit"), second);
        } finally {
            bus.close();
        }
    }

    /**
     * 按实例身份取消后，被取消的实例在内存后端<b>恰好不再收到</b>、另一实例仍<b>恰好一次</b>收到
     * （§5.6 A3 取消语义 + B4 精确一次）：契约基类放宽为「至少收到 / 不含被取消者」，内存后端补回集合精确相等。
     */
    @Test
    public void shouldRemoveByInstanceIdentityExactlyOnce() {
        MessageBus bus = newBus();
        try {
            List<String> received = new ArrayList<>();
            MessageListener listenerA = message -> received.add("A");
            MessageListener listenerB = message -> received.add("B");
            bus.consumer().subscribe("t", listenerA);
            bus.consumer().subscribe("t", listenerB);

            bus.consumer().unsubscribe("t", listenerA);
            bus.producer().send("t", "x");

            assertEquals(Collections.singletonList("B"), received);
        } finally {
            bus.close();
        }
    }

    /**
     * 内存后端总线支持重复创建且各自独立计数。
     */
    @Test
    public void shouldKeepIndependentStatePerBus() {
        MessageBus first = newBus();
        MessageBus second = newBus();
        try {
            String firstId = first.producer().send("t", "1").messageId();
            String secondId = second.producer().send("t", "1").messageId();

            assertEquals("1", firstId);
            assertEquals("1", secondId);
        } finally {
            first.close();
            second.close();
        }
    }

    /**
     * 取消该主题最后一个监听器后，保留该主题的空登记列表（不移除主题键）。
     */
    @Test
    public void shouldKeepEmptyRegistrationAfterUnsubscribingLastListener() throws Exception {
        MemoryMessageBus bus = newMemoryBus();
        try {
            MessageListener listener = message -> {
            };
            bus.consumer().subscribe("t", listener);
            bus.consumer().unsubscribe("t", listener);

            Map<String, List<MessageListener>> subscriptions = subscriptionsOf(bus);
            assertTrue(subscriptions.containsKey("t"));
            assertNotNull(subscriptions.get("t"));
            assertTrue(subscriptions.get("t").isEmpty());
        } finally {
            bus.close();
        }
    }

    /**
     * 创建一个内存后端总线。
     *
     * @return 新的内存消息总线
     */
    private static MemoryMessageBus newMemoryBus() {
        MqConfig config = MqConfig.parse(new Properties(), new MqProviderRegistry().types());
        return new MemoryMessageBus(config);
    }

    /**
     * 读取内存总线的订阅登记表（仅用于测试断言后端实现细节）。
     *
     * @param bus 内存消息总线
     * @return 主题到监听器列表的订阅登记表
     * @throws Exception 反射访问失败
     */
    @SuppressWarnings("unchecked")
    private static Map<String, List<MessageListener>> subscriptionsOf(MemoryMessageBus bus) throws Exception {
        Field field = MemoryMessageBus.class.getDeclaredField("subscriptions");
        field.setAccessible(true);
        return (Map<String, List<MessageListener>>) field.get(bus);
    }
}
