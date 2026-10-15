/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RFC6455 帧编解码的唯一定义点（RFC6455 §5.2 / §5.4 / §5.5）：opcode / FIN / 掩码、7 / 16 / 64 位长度字段、
 * 控制帧约束与关闭帧载荷都在本类实现一次。
 *
 * <p>掩码规则按 RFC6455：<b>客户端发出的帧必须掩码、服务端发出的帧不得掩码</b>；因此解码侧按角色传入
 * {@code requireMask}——服务端读客户端帧时传 {@code true}，客户端读服务端帧时传 {@code false}。分片聚合的
 * 16 MiB 上限常量也定义于此（单帧超限与聚合超限都以关闭码 {@code 1009} 表达），但累加判定由
 * {@link JdkWebSocketConnection} 调用本类常量完成。本类属内部实现，不是公共 API。</p>
 *
 * <p>线程安全：编码与解码方法无共享状态，可被多线程并发调用；同一输入流的解码必须由同一线程串行进行。</p>
 */
public final class Rfc6455FrameCodec {

    /**
     * opcode：延续帧。
     */
    public static final int OPCODE_CONTINUATION = 0x0;

    /**
     * opcode：文本数据帧。
     */
    public static final int OPCODE_TEXT = 0x1;

    /**
     * opcode：二进制数据帧。
     */
    public static final int OPCODE_BINARY = 0x2;

    /**
     * opcode：关闭控制帧。
     */
    public static final int OPCODE_CLOSE = 0x8;

    /**
     * opcode：ping 控制帧。
     */
    public static final int OPCODE_PING = 0x9;

    /**
     * opcode：pong 控制帧。
     */
    public static final int OPCODE_PONG = 0xA;

    /**
     * 控制帧载荷与分片聚合的上限（字节）：固定 16 MiB，不暴露为配置项。
     */
    public static final int MAX_AGGREGATED_BYTES = 16 * 1024 * 1024;

    /**
     * 7 位长度字段的最大值；超过即改用 16 位扩展长度。
     */
    public static final int MAX_SEVEN_BIT_LENGTH = 125;

    /**
     * 控制帧载荷的最大长度（RFC6455 要求控制帧载荷不超过 125 字节且不得分片）。
     */
    private static final int MAX_CONTROL_PAYLOAD = 125;

    /**
     * 关闭码：协议错误。
     */
    public static final int CLOSE_PROTOCOL_ERROR = 1002;

    /**
     * 关闭码：载荷数据非法（如文本不是合法 UTF-8）。
     */
    public static final int CLOSE_INVALID_PAYLOAD = 1007;

    /**
     * 关闭码：消息过大。
     */
    public static final int CLOSE_MESSAGE_TOO_BIG = 1009;

    /**
     * 无关闭码（关闭帧无载荷）时的取值。
     */
    public static final int NO_CLOSE_CODE = -1;

    /**
     * 关闭帧中原因文本的最大字节数（125 减去 2 字节关闭码）。
     */
    private static final int MAX_CLOSE_REASON_BYTES = 123;

    /**
     * 禁止创建工具类实例。
     */
    private Rfc6455FrameCodec() {
    }

    /**
     * 判断 opcode 是否为控制帧（close / ping / pong）。
     *
     * @param opcode 帧 opcode
     * @return 为控制帧时 {@code true}
     */
    public static boolean isControl(int opcode) {
        return (opcode & 0x08) != 0;
    }

    /**
     * 编码一个完整的 WebSocket 帧。
     *
     * @param opcode 帧 opcode
     * @param fin 是否为消息的最后一帧
     * @param payload 载荷；可为 {@code null} 或空
     * @param masked 是否对载荷加掩码（客户端发帧必须为 {@code true}）
     * @return 编码后的帧字节；不为 {@code null}
     */
    public static byte[] encode(int opcode, boolean fin, byte[] payload, boolean masked) {
        return encode(opcode, fin, payload, 0, payload == null ? 0 : payload.length, masked);
    }

    /**
     * 编码一个完整的 WebSocket 帧（可只取载荷的一段）。
     *
     * @param opcode 帧 opcode
     * @param fin 是否为消息的最后一帧
     * @param payload 载荷
     * @param offset 载荷起始下标
     * @param length 载荷长度
     * @param masked 是否对载荷加掩码（客户端发帧必须为 {@code true}）
     * @return 编码后的帧字节；不为 {@code null}
     */
    public static byte[] encode(int opcode, boolean fin, byte[] payload, int offset, int length, boolean masked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(length + 16);
        out.write((fin ? 0x80 : 0x00) | (opcode & 0x0F));
        int maskFlag = masked ? 0x80 : 0x00;
        if (length <= MAX_SEVEN_BIT_LENGTH) {
            out.write(maskFlag | length);
        } else if (length <= 0xFFFF) {
            out.write(maskFlag | 126);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(maskFlag | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) (((long) length >>> shift) & 0xFFL));
            }
        }
        if (!masked) {
            if (payload != null) {
                out.write(payload, offset, length);
            }
            return out.toByteArray();
        }
        byte[] key = new byte[4];
        ThreadLocalRandom.current().nextBytes(key);
        out.write(key, 0, 4);
        for (int i = 0; i < length; i++) {
            out.write(payload[offset + i] ^ key[i % 4]);
        }
        return out.toByteArray();
    }

    /**
     * 从输入流解码一个帧；读到流首即返回 {@code null}（表示对端已关闭且无更多帧）。
     *
     * @param in 源输入流
     * @param requireMask 本侧是否要求入站帧带掩码（服务端读客户端帧为 {@code true}）
     * @return 解码出的帧；流首即结束时为 {@code null}
     * @throws IOException 读取失败或连接提前结束
     * @throws ProtocolException 帧格式、opcode、掩码或长度非法
     */
    public static Frame decode(InputStream in, boolean requireMask) throws IOException {
        int first = in.read();
        if (first == -1) {
            return null;
        }
        int second = readByte(in, "frame length byte");
        boolean fin = (first & 0x80) != 0;
        if ((first & 0x70) != 0) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "reserved frame bits must be zero");
        }
        int opcode = first & 0x0F;
        if (!isKnownOpcode(opcode)) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "unknown frame opcode: " + opcode);
        }
        boolean masked = (second & 0x80) != 0;
        int length = second & 0x7F;
        boolean control = isControl(opcode);
        if (control && (length == 126 || length == 127)) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "control frame must not use extended length");
        }
        if (length == 126) {
            length = (readByte(in, "extended length byte") << 8) | readByte(in, "extended length byte");
        } else if (length == 127) {
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (readByte(in, "extended length byte") & 0xFFL);
            }
            if (value < 0L) {
                throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "frame length must not use the high bit");
            }
            if (value > MAX_AGGREGATED_BYTES) {
                throw new ProtocolException(CLOSE_MESSAGE_TOO_BIG, "frame payload is too big: " + value);
            }
            length = (int) value;
        }
        if (control && length > MAX_CONTROL_PAYLOAD) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "control frame payload must not exceed 125 bytes");
        }
        if (control && !fin) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "control frame must not be fragmented");
        }
        if (masked != requireMask) {
            throw new ProtocolException(CLOSE_PROTOCOL_ERROR, "frame mask flag is invalid for this role");
        }
        byte[] key = null;
        if (masked) {
            key = readFully(in, 4, "masking key");
        }
        byte[] payload = readFully(in, length, "frame payload");
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ key[i % 4]);
            }
        }
        return new Frame(opcode, fin, payload);
    }

    /**
     * 解析关闭帧载荷中的关闭码。
     *
     * @param payload 关闭帧载荷；可为 {@code null}
     * @return 关闭码；载荷不足 2 字节时为 {@link #NO_CLOSE_CODE}
     */
    public static int closeCode(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return NO_CLOSE_CODE;
        }
        return ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
    }

    /**
     * 解析关闭帧载荷中的关闭原因。
     *
     * @param payload 关闭帧载荷；可为 {@code null}
     * @return 关闭原因；无原因文本时为 {@code null}
     */
    public static String closeReason(byte[] payload) {
        if (payload == null || payload.length <= 2) {
            return null;
        }
        return new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
    }

    /**
     * 构造关闭帧载荷：2 字节关闭码后接原因文本（超长时截断）。
     *
     * @param code 关闭码
     * @param reason 关闭原因；可为 {@code null}
     * @return 关闭帧载荷；不为 {@code null}
     */
    public static byte[] closePayload(int code, String reason) {
        byte[] reasonBytes = reason == null ? new byte[0] : reason.getBytes(StandardCharsets.UTF_8);
        int reasonLength = Math.min(reasonBytes.length, MAX_CLOSE_REASON_BYTES);
        byte[] payload = new byte[2 + reasonLength];
        payload[0] = (byte) ((code >>> 8) & 0xFF);
        payload[1] = (byte) (code & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonLength);
        return payload;
    }

    /**
     * 按 UTF-8 严格解码文本载荷；不是合法 UTF-8 时抛协议错误（关闭码 {@code 1007}）。
     *
     * @param payload 文本帧载荷
     * @return 解码后的文本
     * @throws ProtocolException 载荷不是合法 UTF-8
     */
    public static String decodeUtf8(byte[] payload) throws ProtocolException {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException e) {
            throw new ProtocolException(CLOSE_INVALID_PAYLOAD, "text payload is not valid UTF-8");
        }
    }

    // ─────────── 内部实现 ───────────

    /**
     * 判断 opcode 是否为 RFC6455 定义的取值（0 / 1 / 2 / 8 / 9 / 10）。
     *
     * @param opcode 帧 opcode
     * @return 为已知 opcode 时 {@code true}
     */
    private static boolean isKnownOpcode(int opcode) {
        return opcode == OPCODE_CONTINUATION || opcode == OPCODE_TEXT || opcode == OPCODE_BINARY
                || opcode == OPCODE_CLOSE || opcode == OPCODE_PING || opcode == OPCODE_PONG;
    }

    /**
     * 读取一个字节；流提前结束时抛 {@link EOFException}。
     *
     * @param in 源输入流
     * @param what 该字节的用途，用于异常消息定位
     * @return 读到的字节（无符号）
     * @throws IOException 读取失败或流提前结束
     */
    private static int readByte(InputStream in, String what) throws IOException {
        int value = in.read();
        if (value == -1) {
            throw new EOFException("unexpected end of stream while reading " + what);
        }
        return value;
    }

    /**
     * 读满指定长度的字节。
     *
     * @param in 源输入流
     * @param length 需要读取的长度
     * @param what 该段数据的用途，用于异常消息定位
     * @return 读到的字节数组
     * @throws IOException 读取失败或流提前结束
     */
    private static byte[] readFully(InputStream in, int length, String what) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read == -1) {
                throw new EOFException("unexpected end of stream while reading " + what);
            }
            offset += read;
        }
        return buffer;
    }

    /**
     * 一个已解码的 WebSocket 帧。
     */
    public static final class Frame {

        private final int opcode;

        private final boolean fin;

        private final byte[] payload;

        private Frame(int opcode, boolean fin, byte[] payload) {
            this.opcode = opcode;
            this.fin = fin;
            this.payload = payload;
        }

        /**
         * 返回帧 opcode。
         *
         * @return 帧 opcode
         */
        public int opcode() {
            return opcode;
        }

        /**
         * 返回该帧是否为消息的最后一帧。
         *
         * @return {@code FIN} 置位时 {@code true}
         */
        public boolean fin() {
            return fin;
        }

        /**
         * 返回已去掩码的载荷。
         *
         * @return 载荷；不为 {@code null}
         */
        public byte[] payload() {
            return payload;
        }
    }

    /**
     * 帧级协议错误：携带应映射的关闭码，由 {@link JdkWebSocketConnection} 统一转为关闭帧与 {@code onClose}。
     */
    public static final class ProtocolException extends IOException {

        private static final long serialVersionUID = 1L;

        private final int closeCode;

        /**
         * 创建一个协议错误。
         *
         * @param closeCode 应映射的关闭码
         * @param message 错误说明；不得含第三方类型名
         */
        public ProtocolException(int closeCode, String message) {
            super(message);
            this.closeCode = closeCode;
        }

        /**
         * 返回应映射的关闭码。
         *
         * @return 关闭码
         */
        public int closeCode() {
            return closeCode;
        }
    }
}
