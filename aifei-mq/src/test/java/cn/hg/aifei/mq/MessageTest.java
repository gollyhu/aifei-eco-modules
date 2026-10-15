/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * {@link Message} 的校验、双向防御性拷贝与值语义。
 */
public class MessageTest {

    /**
     * 正常构造后主题与载荷可读。
     */
    @Test
    public void shouldExposeTopicAndPayload() {
        Message message = Message.of("orders", "hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("orders", message.topic());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), message.payload());
    }

    /**
     * 主题空白或载荷为 {@code null} 时构造失败。
     */
    @Test
    public void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> Message.of(null, new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> Message.of("  ", new byte[] {1}));
        assertThrows(IllegalArgumentException.class, () -> Message.of("t", null));
    }

    /**
     * 入参字节数组被拷贝：构造后修改原数组不影响消息。
     */
    @Test
    public void shouldCopyPayloadOnInput() {
        byte[] payload = new byte[] {1, 2, 3};
        Message message = Message.of("t", payload);

        payload[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, message.payload());
    }

    /**
     * 出参字节数组被拷贝：修改返回的数组不影响消息。
     */
    @Test
    public void shouldCopyPayloadOnOutput() {
        Message message = Message.of("t", new byte[] {1, 2, 3});

        byte[] returned = message.payload();
        returned[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, message.payload());
    }

    /**
     * 相等性按「主题 + 字节内容」判定；内容与长度都参与比较。
     */
    @Test
    public void shouldFollowValueEquality() {
        Message base = Message.of("t", new byte[] {1, 2, 3});
        Message same = Message.of("t", new byte[] {1, 2, 3});

        assertEquals(base, same);
        assertEquals(base.hashCode(), same.hashCode());
        assertNotEquals(base, Message.of("other", new byte[] {1, 2, 3}));
        assertNotEquals(base, Message.of("t", new byte[] {1, 2, 4}));
        assertNotEquals(base, Message.of("t", new byte[] {1, 2}));
        assertNotEquals(base, null);
        assertNotEquals(base, "t");
    }
}
