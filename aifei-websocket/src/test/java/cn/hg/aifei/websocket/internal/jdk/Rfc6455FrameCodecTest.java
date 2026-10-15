/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * RFC6455 帧编解码的单元测试（设计 §5.3）：掩码、长度字段 7 / 16 / 64 位与边界、部分读取、控制帧约束、
 * 非法 opcode 与关闭帧载荷。
 */
public class Rfc6455FrameCodecTest {

    /**
     * 7 位长度字段的上界。
     */
    private static final int SEVEN_BIT_MAX = 125;

    /**
     * 16 位长度字段的上界。
     */
    private static final int SIXTEEN_BIT_MAX = 0xFFFF;

    /**
     * 客户端发出的文本帧必须带掩码，且解码后载荷一致。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldMaskClientFrameAndRoundTrip() throws IOException {
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);
        byte[] frame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_TEXT, true, payload, true);
        assertTrue("客户端帧必须置掩码位", (frame[1] & 0x80) != 0);
        assertFalse("掩码后线缆上不得出现原文", contains(frame, payload));

        Rfc6455FrameCodec.Frame decoded = Rfc6455FrameCodec.decode(stream(frame), true);
        assertEquals(Rfc6455FrameCodec.OPCODE_TEXT, decoded.opcode());
        assertTrue(decoded.fin());
        assertArrayEquals(payload, decoded.payload());
    }

    /**
     * 服务端发出的帧不带掩码，且解码后载荷一致。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldEncodeServerFrameWithoutMask() throws IOException {
        byte[] payload = new byte[] {1, 2, 3};
        byte[] frame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_BINARY, true, payload, false);
        assertEquals(0, frame[1] & 0x80);

        Rfc6455FrameCodec.Frame decoded = Rfc6455FrameCodec.decode(stream(frame), false);
        assertEquals(Rfc6455FrameCodec.OPCODE_BINARY, decoded.opcode());
        assertArrayEquals(payload, decoded.payload());
    }

    /**
     * 掩码位与角色不符即协议错误（关闭码 1002）：服务端收到未掩码帧、客户端收到掩码帧都不合法。
     */
    @Test
    public void shouldRejectUnexpectedMaskFlag() {
        byte[] masked = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_TEXT, true, new byte[] {7}, true);
        assertProtocolError(masked, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);

        byte[] plain = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_TEXT, true, new byte[] {7}, false);
        assertProtocolError(plain, true, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 长度字段的 125 / 126 边界：125 走 7 位、126 走 16 位，解码都还原出原载荷。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldSupportSevenAndSixteenBitLengthBoundary() throws IOException {
        byte[] small = bytes(SEVEN_BIT_MAX);
        byte[] smallFrame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_BINARY, true, small, false);
        assertEquals(SEVEN_BIT_MAX, smallFrame[1] & 0x7F);
        assertArrayEquals(small, Rfc6455FrameCodec.decode(stream(smallFrame), false).payload());

        byte[] medium = bytes(SEVEN_BIT_MAX + 1);
        byte[] mediumFrame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_BINARY, true, medium, false);
        assertEquals(126, mediumFrame[1] & 0x7F);
        assertArrayEquals(medium, Rfc6455FrameCodec.decode(stream(mediumFrame), false).payload());

        byte[] large = bytes(SIXTEEN_BIT_MAX);
        byte[] largeFrame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_BINARY, true, large, false);
        assertEquals(126, largeFrame[1] & 0x7F);
        assertArrayEquals(large, Rfc6455FrameCodec.decode(stream(largeFrame), false).payload());
    }

    /**
     * 长度字段的 127 边界：超过 16 位上界即改用 64 位长度，解码还原出原载荷。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldSupportSixtyFourBitLength() throws IOException {
        byte[] payload = bytes(SIXTEEN_BIT_MAX + 1);
        byte[] frame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_BINARY, true, payload, false);
        assertEquals(127, frame[1] & 0x7F);
        assertArrayEquals(payload, Rfc6455FrameCodec.decode(stream(frame), false).payload());
    }

    /**
     * 64 位长度超过聚合上限即按「消息过大」（关闭码 1009）失败，且不尝试读取载荷。
     */
    @Test
    public void shouldRejectFrameLargerThanAggregationLimit() {
        byte[] header = new byte[10];
        header[0] = (byte) 0x82;
        header[1] = (byte) 0x7F;
        long tooBig = (long) Rfc6455FrameCodec.MAX_AGGREGATED_BYTES + 1L;
        for (int i = 0; i < 8; i++) {
            header[2 + i] = (byte) ((tooBig >>> (56 - 8 * i)) & 0xFFL);
        }
        assertProtocolError(header, false, Rfc6455FrameCodec.CLOSE_MESSAGE_TOO_BIG);
    }

    /**
     * 64 位长度的最高位必须为零，否则按协议错误处理。
     */
    @Test
    public void shouldRejectLengthWithHighBitSet() {
        byte[] header = new byte[10];
        header[0] = (byte) 0x82;
        header[1] = (byte) 0x7F;
        header[2] = (byte) 0x80;
        assertProtocolError(header, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 输入流每次只给出 1 字节时也必须能解码出完整帧（部分读取）。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldDecodeAcrossPartialReads() throws IOException {
        byte[] payload = "partial-read-payload".getBytes(StandardCharsets.UTF_8);
        byte[] frame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_TEXT, true, payload, true);

        Rfc6455FrameCodec.Frame decoded = Rfc6455FrameCodec.decode(new OneByteStream(frame), true);
        assertArrayEquals(payload, decoded.payload());
    }

    /**
     * 控制帧必须 {@code FIN = 1}：分片的控制帧按协议错误处理。
     */
    @Test
    public void shouldRejectFragmentedControlFrame() {
        byte[] frame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_PING, false, new byte[] {1}, false);
        assertProtocolError(frame, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 控制帧载荷不得超过 125 字节。
     */
    @Test
    public void shouldRejectOversizedControlFrame() {
        byte[] frame = new byte[4 + SEVEN_BIT_MAX + 1];
        frame[0] = (byte) 0x8A;
        frame[1] = (byte) (SEVEN_BIT_MAX + 1);
        assertProtocolError(frame, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 控制帧不得使用扩展长度字段。
     */
    @Test
    public void shouldRejectControlFrameWithExtendedLength() {
        byte[] frame = new byte[2];
        frame[0] = (byte) 0x89;
        frame[1] = (byte) 126;
        assertProtocolError(frame, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 未定义的 opcode 按协议错误处理。
     */
    @Test
    public void shouldRejectUnknownOpcode() {
        byte[] frame = new byte[] {(byte) 0x83, 0x00};
        assertProtocolError(frame, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 保留位非零按协议错误处理。
     */
    @Test
    public void shouldRejectReservedBits() {
        byte[] frame = new byte[] {(byte) 0x91, 0x00};
        assertProtocolError(frame, false, Rfc6455FrameCodec.CLOSE_PROTOCOL_ERROR);
    }

    /**
     * 流首即结束时返回 {@code null}（对端关闭且无更多帧）。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldReturnNullAtEndOfStream() throws IOException {
        assertNull(Rfc6455FrameCodec.decode(stream(new byte[0]), false));
    }

    /**
     * 空文本帧解码为空串。
     *
     * @throws IOException 解码失败
     */
    @Test
    public void shouldDecodeEmptyTextFrame() throws IOException {
        byte[] frame = Rfc6455FrameCodec.encode(Rfc6455FrameCodec.OPCODE_TEXT, true, new byte[0], false);
        Rfc6455FrameCodec.Frame decoded = Rfc6455FrameCodec.decode(stream(frame), false);
        assertEquals("", Rfc6455FrameCodec.decodeUtf8(decoded.payload()));
    }

    /**
     * 文本载荷不是合法 UTF-8 时按「载荷非法」（关闭码 1007）失败。
     */
    @Test
    public void shouldRejectInvalidUtf8TextPayload() {
        try {
            Rfc6455FrameCodec.decodeUtf8(new byte[] {(byte) 0xC3, (byte) 0x28});
            fail("非法 UTF-8 应被拒绝");
        } catch (Rfc6455FrameCodec.ProtocolException expected) {
            assertEquals(Rfc6455FrameCodec.CLOSE_INVALID_PAYLOAD, expected.closeCode());
        }
    }

    /**
     * 关闭帧载荷的关闭码与原因可往返解析。
     */
    @Test
    public void shouldRoundTripClosePayload() {
        byte[] payload = Rfc6455FrameCodec.closePayload(1000, "bye");
        assertEquals(1000, Rfc6455FrameCodec.closeCode(payload));
        assertEquals("bye", Rfc6455FrameCodec.closeReason(payload));
    }

    /**
     * 关闭帧无载荷时关闭码为 {@code -1}、原因为 {@code null}。
     */
    @Test
    public void shouldReportNoCloseCodeWhenPayloadEmpty() {
        assertEquals(Rfc6455FrameCodec.NO_CLOSE_CODE, Rfc6455FrameCodec.closeCode(new byte[0]));
        assertEquals(Rfc6455FrameCodec.NO_CLOSE_CODE, Rfc6455FrameCodec.closeCode(new byte[] {10}));
        assertNull(Rfc6455FrameCodec.closeReason(new byte[] {3, (byte) 0xE8}));
        assertNull(Rfc6455FrameCodec.closeReason(null));
    }

    /**
     * 关闭原因超长时截断到 123 字节，保证控制帧载荷不超过 125 字节。
     */
    @Test
    public void shouldTruncateLongCloseReason() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            builder.append('x');
        }
        byte[] payload = Rfc6455FrameCodec.closePayload(1001, builder.toString());
        assertEquals(125, payload.length);
        assertEquals(1001, Rfc6455FrameCodec.closeCode(payload));
        assertEquals(123, Rfc6455FrameCodec.closeReason(payload).length());
    }

    /**
     * 控制帧判定：ping / pong / close 为控制帧，文本 / 二进制 / 延续为数据帧。
     */
    @Test
    public void shouldIdentifyControlOpcodes() {
        assertTrue(Rfc6455FrameCodec.isControl(Rfc6455FrameCodec.OPCODE_CLOSE));
        assertTrue(Rfc6455FrameCodec.isControl(Rfc6455FrameCodec.OPCODE_PING));
        assertTrue(Rfc6455FrameCodec.isControl(Rfc6455FrameCodec.OPCODE_PONG));
        assertFalse(Rfc6455FrameCodec.isControl(Rfc6455FrameCodec.OPCODE_TEXT));
        assertFalse(Rfc6455FrameCodec.isControl(Rfc6455FrameCodec.OPCODE_BINARY));
        assertFalse(Rfc6455FrameCodec.isControl(Rfc6455FrameCodec.OPCODE_CONTINUATION));
    }

    // ─────────── 断言工具 ───────────

    /**
     * 断言给定帧字节解码时抛协议错误，且关闭码为期望值。
     *
     * @param frame 帧字节
     * @param requireMask 解码侧是否要求掩码
     * @param expectedCloseCode 期望的关闭码
     */
    private static void assertProtocolError(byte[] frame, boolean requireMask, int expectedCloseCode) {
        try {
            Rfc6455FrameCodec.decode(stream(frame), requireMask);
            fail("应抛出协议错误");
        } catch (Rfc6455FrameCodec.ProtocolException expected) {
            assertEquals(expectedCloseCode, expected.closeCode());
        } catch (IOException e) {
            fail("应抛出协议错误，实际为 " + e);
        }
    }

    /**
     * 判断字节数组中是否包含子序列。
     *
     * @param data 被查找的字节数组
     * @param pattern 子序列
     * @return 含子序列时 {@code true}
     */
    private static boolean contains(byte[] data, byte[] pattern) {
        for (int i = 0; i + pattern.length <= data.length; i++) {
            boolean matched = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构造指定长度的字节序列；取值按下标递增，便于定位错位。
     *
     * @param length 长度
     * @return 字节序列
     */
    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    /**
     * 由字节构造输入流。
     *
     * @param data 帧字节
     * @return 输入流
     */
    private static InputStream stream(byte[] data) {
        return new ByteArrayInputStream(data);
    }

    /**
     * 每次只吐出 1 字节的输入流：用于验证解码能跨多次部分读取。
     */
    private static final class OneByteStream extends InputStream {

        private final byte[] data;

        private int index;

        private OneByteStream(byte[] data) {
            this.data = data;
        }

        @Override
        public int read() {
            if (index >= data.length) {
                return -1;
            }
            return data[index++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (index >= data.length) {
                return -1;
            }
            if (length <= 0) {
                return 0;
            }
            buffer[offset] = data[index++];
            return 1;
        }
    }
}
