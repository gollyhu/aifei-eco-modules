/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.hg.aifei.mq.internal.MqHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link MqHolder} 的登记、取用与清除，以及未登记时的状态异常。
 */
public class MqHolderTest {

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
     * 未登记时取生产者与消费者都抛 {@link IllegalStateException}。
     */
    @Test
    public void shouldFailWhenNotRegistered() {
        assertThrows(IllegalStateException.class, MqHolder::producer);
        assertThrows(IllegalStateException.class, MqHolder::consumer);
    }

    /**
     * 登记后取到同一实例。
     */
    @Test
    public void shouldReturnRegisteredInstances() {
        Producer producer = new StubProducer();
        Consumer consumer = (topic, listener) -> {
        };
        MqHolder.register(producer, consumer);

        assertSame(producer, MqHolder.producer());
        assertSame(consumer, MqHolder.consumer());
    }

    /**
     * 清除后取用立即失败。
     */
    @Test
    public void shouldFailAfterClear() {
        MqHolder.register(new StubProducer(), (topic, listener) -> {
        });

        MqHolder.clear();

        assertThrows(IllegalStateException.class, MqHolder::producer);
        assertThrows(IllegalStateException.class, MqHolder::consumer);
    }

    /**
     * 登记 {@code null} 参数时失败。
     */
    @Test
    public void shouldRejectNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> MqHolder.register(null, (topic, listener) -> {
                }));
        assertThrows(IllegalArgumentException.class,
                () -> MqHolder.register(new StubProducer(), null));
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
}
