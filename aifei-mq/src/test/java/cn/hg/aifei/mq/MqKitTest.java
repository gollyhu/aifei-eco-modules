/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.hg.aifei.mq.internal.MqHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link MqKit} 的未启动状态异常与对已登记实例的转发。
 */
public class MqKitTest {

    /**
     * 每个用例前清空登记，模拟未启动。
     */
    @Before
    public void setUp() {
        MqHolder.clear();
    }

    /**
     * 每个用例后清空登记。
     */
    @After
    public void tearDown() {
        MqHolder.clear();
    }

    /**
     * 未启动时取实例与发送、订阅都抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldFailBeforeStart() {
        assertThrows(IllegalStateException.class, MqKit::producer);
        assertThrows(IllegalStateException.class, MqKit::consumer);
        assertThrows(IllegalStateException.class, () -> MqKit.send("t", "x"));
        assertThrows(IllegalStateException.class, () -> MqKit.send("t", new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> MqKit.send(Message.of("t", new byte[] {1})));
        assertThrows(IllegalStateException.class, () -> MqKit.subscribe("t", message -> {
        }));
    }

    /**
     * 已登记时门面只做转发，不重复校验、不改写参数。
     */
    @Test
    public void shouldForwardToRegisteredInstances() {
        RecordingProducer producer = new RecordingProducer();
        RecordingConsumer consumer = new RecordingConsumer();
        MqHolder.register(producer, consumer);

        SendResult textResult = MqKit.send("t1", "hello");
        assertEquals("t1", producer.topic);
        assertEquals("hello", producer.text);
        assertEquals("t1", textResult.topic());
        assertEquals(RecordingProducer.MESSAGE_ID, textResult.messageId());

        MqKit.send("t2", new byte[] {1, 2});
        assertEquals("t2", producer.topic);
        assertArrayEquals(new byte[] {1, 2}, producer.payloadBytes);

        Message message = Message.of("t3", new byte[] {3});
        MqKit.send(message);
        assertSame(message, producer.message);

        MessageListener listener = received -> {
        };
        MqKit.subscribe("s1", listener);
        assertEquals("s1", consumer.topic);
        assertSame(listener, consumer.listener);

        assertSame(producer, MqKit.producer());
        assertSame(consumer, MqKit.consumer());
    }

    /**
     * 记录调用的生产者。
     */
    private static final class RecordingProducer implements Producer {

        /**
         * 固定返回的消息标识。
         */
        private static final String MESSAGE_ID = "1";

        private String topic;

        private String text;

        private byte[] payloadBytes;

        private Message message;

        @Override
        public SendResult send(Message message) {
            this.message = message;
            return SendResult.of(message.topic(), MESSAGE_ID);
        }

        @Override
        public SendResult send(String topic, byte[] payload) {
            this.topic = topic;
            this.payloadBytes = payload;
            return SendResult.of(topic, MESSAGE_ID);
        }

        @Override
        public SendResult send(String topic, String text) {
            this.topic = topic;
            this.text = text;
            return SendResult.of(topic, MESSAGE_ID);
        }
    }

    /**
     * 记录调用的消费者。
     */
    private static final class RecordingConsumer implements Consumer {

        private String topic;

        private MessageListener listener;

        @Override
        public void subscribe(String topic, MessageListener listener) {
            this.topic = topic;
            this.listener = listener;
        }
    }
}
