/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/**
 * {@link SendResult} 的校验与值语义。
 */
public class SendResultTest {

    /**
     * 正常构造后主题与消息标识可读。
     */
    @Test
    public void shouldExposeTopicAndMessageId() {
        SendResult result = SendResult.of("orders", "1");

        assertEquals("orders", result.topic());
        assertEquals("1", result.messageId());
    }

    /**
     * 主题或消息标识空白时构造失败。
     */
    @Test
    public void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> SendResult.of(null, "1"));
        assertThrows(IllegalArgumentException.class, () -> SendResult.of("  ", "1"));
        assertThrows(IllegalArgumentException.class, () -> SendResult.of("t", null));
        assertThrows(IllegalArgumentException.class, () -> SendResult.of("t", "  "));
    }

    /**
     * 相等性按「主题 + 消息标识」判定。
     */
    @Test
    public void shouldFollowValueEquality() {
        SendResult base = SendResult.of("t", "1");
        SendResult same = SendResult.of("t", "1");

        assertEquals(base, same);
        assertEquals(base.hashCode(), same.hashCode());
        assertNotEquals(base, SendResult.of("other", "1"));
        assertNotEquals(base, SendResult.of("t", "2"));
        assertNotEquals(base, null);
        assertNotEquals(base, "t");
    }
}
