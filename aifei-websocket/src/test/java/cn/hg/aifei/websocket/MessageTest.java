/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * {@link Message} 的不可变性、类型判定、防御性拷贝与相等性契约（设计 §4.7）。
 */
public class MessageTest {

    /**
     * 文本消息的入参 {@code null} 必须抛用户错误。
     */
    @Test
    public void shouldRejectNullText() {
        assertThrows(IllegalArgumentException.class, () -> Message.text(null));
    }

    /**
     * 二进制消息的入参 {@code null} 必须抛用户错误。
     */
    @Test
    public void shouldRejectNullBinary() {
        assertThrows(IllegalArgumentException.class, () -> Message.binary(null));
    }

    /**
     * 文本与二进制的类型判定互相排斥，且取值语义正确。
     */
    @Test
    public void shouldReportType() {
        Message text = Message.text("hello");
        Message binary = Message.binary(new byte[] {1, 2, 3});

        assertTrue(text.isText());
        assertFalse(binary.isText());
    }

    /**
     * 类型不符时取值必须抛 {@link IllegalStateException}，避免把错配内容当作合法载荷。
     */
    @Test
    public void shouldRejectMismatchedAccessor() {
        Message text = Message.text("hello");
        Message binary = Message.binary(new byte[] {1, 2, 3});

        assertEquals("hello", text.asText());
        assertArrayEquals(new byte[] {1, 2, 3}, binary.asBytes());

        assertThrows(IllegalStateException.class, text::asBytes);
        assertThrows(IllegalStateException.class, binary::asText);
    }

    /**
     * 二进制入参的后续修改不得影响已构造的消息（入参防御性拷贝）。
     */
    @Test
    public void shouldCopyBinaryOnConstruction() {
        byte[] source = new byte[] {1, 2, 3};
        Message message = Message.binary(source);

        source[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, message.asBytes());
    }

    /**
     * {@code asBytes()} 的返回值是副本，修改它不得影响消息内容（出参防御性拷贝）。
     */
    @Test
    public void shouldCopyBinaryOnAccess() {
        Message message = Message.binary(new byte[] {1, 2, 3});

        byte[] first = message.asBytes();
        first[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, message.asBytes());
    }

    /**
     * {@code size()} 对文本按 UTF-8 编码后字节数计算，对二进制按原始长度计算。
     */
    @Test
    public void shouldReportUtf8Size() {
        assertEquals(0, Message.text("").size());
        assertEquals(3, Message.text("abc").size());
        assertEquals("中".getBytes(StandardCharsets.UTF_8).length, Message.text("中").size());
        assertEquals("中文".getBytes(StandardCharsets.UTF_8).length, Message.text("中文").size());
        assertEquals(6, Message.text("中文").size());
        assertEquals(5, Message.binary(new byte[5]).size());
    }

    /**
     * 相等性按「类型 + 内容」判定：同类型同内容相等，文本与二进制永不相等。
     */
    @Test
    public void shouldCompareByTypeAndContent() {
        assertEquals(Message.text("same"), Message.text("same"));
        assertEquals(Message.binary(new byte[] {1, 2}), Message.binary(new byte[] {1, 2}));
        assertNotEquals(Message.text("a"), Message.text("b"));
        assertNotEquals(Message.binary(new byte[] {1}), Message.binary(new byte[] {2}));
        assertNotEquals(Message.text("ab"), Message.binary("ab".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 相等的消息必须拥有一致的 {@code hashCode}，以便作为哈希键使用。
     */
    @Test
    public void shouldKeepHashCodeConsistentWithEquals() {
        assertEquals(Message.text("x").hashCode(), Message.text("x").hashCode());
        assertEquals(Message.binary(new byte[] {7, 8}).hashCode(), Message.binary(new byte[] {7, 8}).hashCode());
    }
}
