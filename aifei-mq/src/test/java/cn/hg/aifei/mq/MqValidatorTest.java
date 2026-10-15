/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertThrows;

import cn.hg.aifei.mq.internal.MqValidator;
import org.junit.Test;

/**
 * {@link MqValidator} 各校验方法的参数边界与异常类型。
 */
public class MqValidatorTest {

    /**
     * 主题为 {@code null} 或只含空白时失败，合法主题通过。
     */
    @Test
    public void shouldValidateTopic() {
        MqValidator.requireTopic("orders");

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireTopic(null));
        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireTopic(""));
        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireTopic("   "));
    }

    /**
     * 载荷为 {@code null} 时失败，空数组属合法。
     */
    @Test
    public void shouldValidatePayload() {
        MqValidator.requirePayload(new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requirePayload(null));
    }

    /**
     * 文本为 {@code null} 时失败，空串属合法。
     */
    @Test
    public void shouldValidateText() {
        MqValidator.requireText("");

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireText(null));
    }

    /**
     * 消息为 {@code null} 时失败。
     */
    @Test
    public void shouldValidateMessage() {
        MqValidator.requireMessage(Message.of("t", new byte[] {1}));

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireMessage(null));
    }

    /**
     * 消息标识为 {@code null} 或只含空白时失败。
     */
    @Test
    public void shouldValidateMessageId() {
        MqValidator.requireMessageId("1");

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireMessageId(null));
        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireMessageId(""));
        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireMessageId("   "));
    }

    /**
     * 监听器为 {@code null} 时失败。
     */
    @Test
    public void shouldValidateListener() {
        MqValidator.requireListener(message -> {
        });

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireListener(null));
    }

    /**
     * 生产者为 {@code null} 时失败。
     */
    @Test
    public void shouldValidateProducer() {
        MqValidator.requireProducer(new StubProducer());

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireProducer(null));
    }

    /**
     * 消费者为 {@code null} 时失败。
     */
    @Test
    public void shouldValidateConsumer() {
        MqValidator.requireConsumer(new StubConsumer());

        assertThrows(IllegalArgumentException.class, () -> MqValidator.requireConsumer(null));
    }

    /**
     * 仅用于校验通过的最小生产者。
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
     * 仅用于校验通过的最小消费者。
     */
    private static final class StubConsumer implements Consumer {

        @Override
        public void subscribe(String topic, MessageListener listener) {
        }
    }
}
