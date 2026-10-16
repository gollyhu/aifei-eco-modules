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

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ProducerUtil} 的未启动状态异常、实例名参数校验与对已登记实例的转发。
 */
public class ProducerUtilTest {

    /**
     * 命名实例名；主实例名用 {@link MqConfig#MAIN_INSTANCE_NAME}。
     */
    private static final String NAMED = "orders";

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
     * 未启动时取实例与三个发送重载都抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldFailBeforeStart() {
        assertThrows(IllegalStateException.class, ProducerUtil::use);
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use(NAMED));
        assertThrows(IllegalStateException.class, () -> ProducerUtil.send("t", "x"));
        assertThrows(IllegalStateException.class, () -> ProducerUtil.send("t", new byte[] {1}));
        assertThrows(IllegalStateException.class, () -> ProducerUtil.send(Message.of("t", new byte[] {1})));
    }

    /**
     * 判定顺序为先状态、后参数：未启动时即使实例名非法也抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldJudgeStateBeforeInstanceName() {
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use(""));
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use("a.b"));
        assertThrows(IllegalStateException.class, () -> ProducerUtil.use("names"));
    }

    /**
     * 已启动但实例名非法或未注册时抛 {@link IllegalArgumentException}。
     */
    @Test
    public void shouldRejectIllegalOrUnknownInstanceName() {
        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, new RecordingProducer(), new RecordingConsumer());

        assertThrows(IllegalArgumentException.class, () -> ProducerUtil.use("payments"));
        assertThrows(IllegalArgumentException.class, () -> ProducerUtil.use(null));
        assertThrows(IllegalArgumentException.class, () -> ProducerUtil.use(""));
        assertThrows(IllegalArgumentException.class, () -> ProducerUtil.use("a b"));
        assertThrows(IllegalArgumentException.class, () -> ProducerUtil.use("names"));
    }

    /**
     * 已登记时只做转发，不重复校验、不改写参数；取到的就是登记的那个实例。
     */
    @Test
    public void shouldForwardToRegisteredInstances() {
        RecordingProducer main = new RecordingProducer();
        RecordingProducer named = new RecordingProducer();
        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, main, new RecordingConsumer());
        MqHolder.register(NAMED, named, new RecordingConsumer());

        SendResult textResult = ProducerUtil.send("t1", "hello");
        assertEquals("t1", main.topic);
        assertEquals("hello", main.text);
        assertEquals("t1", textResult.topic());
        assertEquals(RecordingProducer.MESSAGE_ID, textResult.messageId());

        ProducerUtil.send("t2", new byte[] {1, 2});
        assertEquals("t2", main.topic);
        assertArrayEquals(new byte[] {1, 2}, main.payloadBytes);

        Message message = Message.of("t3", new byte[] {3});
        ProducerUtil.send(message);
        assertSame(message, main.message);

        assertSame(main, ProducerUtil.use());
        assertSame(named, ProducerUtil.use(NAMED));
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
     * 仅用于凑齐登记的消费者。
     */
    private static final class RecordingConsumer implements Consumer {

        @Override
        public void subscribe(String topic, MessageListener listener) {
        }

        @Override
        public void unsubscribe(String topic, MessageListener listener) {
        }
    }
}
