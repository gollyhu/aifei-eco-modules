/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link MqHolder} 的多实例登记、按名取用与清除，以及取用期的状态 / 参数异常。
 */
public class MqHolderTest {

    /**
     * 命名实例名；主实例名用 {@link MqConfig#MAIN_INSTANCE_NAME}。
     */
    private static final String NAMED = "orders";

    /**
     * 每个用例前清空登记，避免静态状态串场。
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
     * 未登记任何实例时按名取生产者与消费者都抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldFailWhenNotRegistered() {
        assertThrows(IllegalStateException.class, () -> MqHolder.producer(MqConfig.MAIN_INSTANCE_NAME));
        assertThrows(IllegalStateException.class, () -> MqHolder.consumer(MqConfig.MAIN_INSTANCE_NAME));
    }

    /**
     * 登记后按名取到同一实例，且各实例互不串场。
     */
    @Test
    public void shouldReturnRegisteredInstances() {
        Producer producer = new StubProducer();
        Consumer consumer = new StubConsumer();
        Producer namedProducer = new StubProducer();
        Consumer namedConsumer = new StubConsumer();
        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, producer, consumer);
        MqHolder.register(NAMED, namedProducer, namedConsumer);

        assertSame(producer, MqHolder.producer(MqConfig.MAIN_INSTANCE_NAME));
        assertSame(consumer, MqHolder.consumer(MqConfig.MAIN_INSTANCE_NAME));
        assertSame(namedProducer, MqHolder.producer(NAMED));
        assertSame(namedConsumer, MqHolder.consumer(NAMED));
    }

    /**
     * 清除后取用立即失败。
     */
    @Test
    public void shouldFailAfterClear() {
        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, new StubProducer(), new StubConsumer());
        MqHolder.register(NAMED, new StubProducer(), new StubConsumer());

        MqHolder.clear();

        assertThrows(IllegalStateException.class, () -> MqHolder.producer(MqConfig.MAIN_INSTANCE_NAME));
        assertThrows(IllegalStateException.class, () -> MqHolder.consumer(MqConfig.MAIN_INSTANCE_NAME));
        assertThrows(IllegalStateException.class, () -> MqHolder.producer(NAMED));
    }

    /**
     * 登记时实例名非法或生产 / 消费者为 {@code null} 都失败。
     */
    @Test
    public void shouldRejectNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, null, new StubConsumer()));
        assertThrows(IllegalArgumentException.class,
                () -> MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, new StubProducer(), null));
        assertThrows(IllegalArgumentException.class,
                () -> MqHolder.register("", new StubProducer(), new StubConsumer()));
    }

    /**
     * 取用期判定顺序为先状态、后参数：未登记前实例名非法也抛状态异常，登记后未注册才抛参数异常。
     */
    @Test
    public void shouldJudgeStateBeforeInstanceName() {
        assertThrows(IllegalStateException.class, () -> MqHolder.producer(""));
        assertThrows(IllegalStateException.class, () -> MqHolder.consumer("a.b"));

        MqHolder.register(MqConfig.MAIN_INSTANCE_NAME, new StubProducer(), new StubConsumer());

        assertThrows(IllegalArgumentException.class, () -> MqHolder.producer(""));
        assertThrows(IllegalArgumentException.class, () -> MqHolder.producer("a b"));
        assertThrows(IllegalArgumentException.class, () -> MqHolder.producer("payments"));
        assertThrows(IllegalArgumentException.class, () -> MqHolder.consumer("payments"));
    }

    /**
     * 仅用于登记测试的最小生产者。
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
     * 仅用于登记测试的最小消费者。
     */
    private static final class StubConsumer implements Consumer {

        @Override
        public void subscribe(String topic, MessageListener listener) {
        }

        @Override
        public void unsubscribe(String topic, MessageListener listener) {
        }
    }
}
