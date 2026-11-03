/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ConsumerKit} 的未启动状态异常、实例名参数校验与对已登记实例的转发。
 */
public class ConsumerKitTest {

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
     * 未启动时取实例、订阅与取消订阅都抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldFailBeforeStart() {
        assertThrows(IllegalStateException.class, ConsumerKit::use);
        assertThrows(IllegalStateException.class, () -> ConsumerKit.use(NAMED));
        assertThrows(IllegalStateException.class, () -> ConsumerKit.subscribe("t", message -> {
        }));
        assertThrows(IllegalStateException.class, () -> ConsumerKit.unsubscribe("t", message -> {
        }));
    }

    /**
     * 判定顺序为先状态、后参数：未启动时即使实例名非法也抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldJudgeStateBeforeInstanceName() {
        assertThrows(IllegalStateException.class, () -> ConsumerKit.use(""));
        assertThrows(IllegalStateException.class, () -> ConsumerKit.use("a:b"));
        assertThrows(IllegalStateException.class, () -> ConsumerKit.use("names"));
    }

    /**
     * 已启动但实例名非法或未注册时抛 {@link IllegalArgumentException}。
     */
    @Test
    public void shouldRejectIllegalOrUnknownInstanceName() {
        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, new StubProducer(), new RecordingConsumer());

        assertThrows(IllegalArgumentException.class, () -> ConsumerKit.use("payments"));
        assertThrows(IllegalArgumentException.class, () -> ConsumerKit.use(null));
        assertThrows(IllegalArgumentException.class, () -> ConsumerKit.use(""));
        assertThrows(IllegalArgumentException.class, () -> ConsumerKit.use("a b"));
        assertThrows(IllegalArgumentException.class, () -> ConsumerKit.use("names"));
    }

    /**
     * 已登记时只做转发，不重复校验、不改写参数；取到的就是登记的那个实例。
     */
    @Test
    public void shouldForwardToRegisteredInstances() {
        RecordingConsumer main = new RecordingConsumer();
        RecordingConsumer named = new RecordingConsumer();
        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, new StubProducer(), main);
        MqHolder.register(NAMED, new StubProducer(), named);

        MessageListener listener = received -> {
        };
        ConsumerKit.subscribe("s1", listener);
        assertEquals("s1", main.subscribedTopic);
        assertSame(listener, main.subscribedListener);

        ConsumerKit.unsubscribe("s1", listener);
        assertEquals("s1", main.unsubscribedTopic);
        assertSame(listener, main.unsubscribedListener);

        assertSame(main, ConsumerKit.use());
        assertSame(named, ConsumerKit.use(NAMED));
    }

    /**
     * 仅用于凑齐登记的最小生产者。
     */
    private static final class StubProducer implements Producer {

        @Override
        public SendResult send(Message message) {
            return null;
        }

        @Override
        public SendResult send(String topic, byte[] payload) {
            return null;
        }

        @Override
        public SendResult send(String topic, String text) {
            return null;
        }
    }

    /**
     * 记录调用的消费者。
     */
    private static final class RecordingConsumer implements Consumer {

        private String subscribedTopic;

        private MessageListener subscribedListener;

        private String unsubscribedTopic;

        private MessageListener unsubscribedListener;

        @Override
        public void subscribe(String topic, MessageListener listener) {
            this.subscribedTopic = topic;
            this.subscribedListener = listener;
        }

        @Override
        public void unsubscribe(String topic, MessageListener listener) {
            this.unsubscribedTopic = topic;
            this.unsubscribedListener = listener;
        }
    }
}
