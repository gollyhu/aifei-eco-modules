/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.mq.internal.MessageBus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * {@link MessageBus} 的行为契约基类：由各后端实现继承运行，覆盖同步投递、保序、订阅顺序、无订阅者、
 * 关闭语义与监听器异常隔离。
 */
public abstract class MessageBusContractTest {

    /**
     * 创建被测总线。
     *
     * @return 一个新的消息总线
     */
    protected abstract MessageBus newBus();

    /**
     * 订阅后发送，订阅者收到同主题、同载荷的消息，并拿到带消息标识的发送结果。
     */
    @Test
    public void shouldDeliverToSubscriber() {
        MessageBus bus = newBus();
        try {
            List<Message> received = new ArrayList<>();
            bus.consumer().subscribe("orders", received::add);
            SendResult result = bus.producer().send("orders", "A1001");

            assertEquals(1, received.size());
            assertEquals("orders", received.get(0).topic());
            assertEquals("A1001", new String(received.get(0).payload(), StandardCharsets.UTF_8));
            assertEquals("orders", result.topic());
            assertNotNull(result.messageId());
        } finally {
            bus.close();
        }
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
     * 无订阅者时发送视为成功，仍返回带消息标识的结果。
     */
    @Test
    public void shouldSucceedWithoutSubscribers() {
        MessageBus bus = newBus();
        try {
            SendResult result = bus.producer().send("none", "x");

            assertEquals("none", result.topic());
            assertNotNull(result.messageId());
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
     * 关闭是幂等的：重复关闭不抛异常，且总线保持一致关闭状态（第二次关闭不会把状态翻回去）。
     */
    @Test
    public void shouldCloseIdempotently() {
        MessageBus bus = newBus();
        bus.close();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.producer().send("t", "x"));
    }

    /**
     * 关闭后发送抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectSendAfterClose() {
        MessageBus bus = newBus();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.producer().send("t", "x"));
    }

    /**
     * 关闭后订阅抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldRejectSubscribeAfterClose() {
        MessageBus bus = newBus();
        bus.close();

        assertThrows(IllegalStateException.class, () -> bus.consumer().subscribe("t", message -> {
        }));
    }

    /**
     * 单个监听器抛出的运行时异常就地隔离，不中断其余订阅者、不上抛给生产者。
     */
    @Test
    public void shouldIsolateListenerException() {
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
     * 以 UTF-8 解码消息载荷。
     *
     * @param message 消息
     * @return 文本载荷
     */
    private static String text(Message message) {
        return new String(message.payload(), StandardCharsets.UTF_8);
    }
}
