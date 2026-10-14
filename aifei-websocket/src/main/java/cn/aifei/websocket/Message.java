/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 一次 WebSocket 数据载荷：文本或二进制二选一，不可变。
 *
 * <p>二进制内容的入参与出参都做防御性拷贝，文本内容要求非 {@code null}。相等性按「类型 + 内容」判定：
 * 文本消息与二进制消息永不相等，即便字节形态一致。</p>
 */
public final class Message {

    /**
     * 是否为文本消息。
     */
    private final boolean text;

    /**
     * 文本内容；非文本消息时为 {@code null}。
     */
    private final String textValue;

    /**
     * 二进制内容；文本消息时为 {@code null}。
     */
    private final byte[] binaryValue;

    /**
     * 禁止外部构造，只能经 {@link #text(String)} / {@link #binary(byte[])} 创建。
     */
    private Message(boolean text, String textValue, byte[] binaryValue) {
        this.text = text;
        this.textValue = textValue;
        this.binaryValue = binaryValue;
    }

    /**
     * 创建一条文本消息。
     *
     * @param text 文本内容
     * @return 文本消息
     * @throws IllegalArgumentException {@code text} 为 {@code null}
     */
    public static Message text(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        return new Message(true, text, null);
    }

    /**
     * 创建一条二进制消息；对入参做防御性拷贝。
     *
     * @param bytes 二进制内容
     * @return 二进制消息
     * @throws IllegalArgumentException {@code bytes} 为 {@code null}
     */
    public static Message binary(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes can not be null");
        }
        return new Message(false, null, bytes.clone());
    }

    /**
     * 返回是否为文本消息。
     *
     * @return 文本消息时 {@code true}
     */
    public boolean isText() {
        return text;
    }

    /**
     * 返回文本内容。
     *
     * @return 文本内容
     * @throws IllegalStateException 本消息为二进制消息
     */
    public String asText() {
        if (!text) {
            throw new IllegalStateException("message is not a text message");
        }
        return textValue;
    }

    /**
     * 返回二进制内容的副本。
     *
     * @return 二进制内容副本
     * @throws IllegalStateException 本消息为文本消息
     */
    public byte[] asBytes() {
        if (text) {
            throw new IllegalStateException("message is not a binary message");
        }
        return binaryValue.clone();
    }

    /**
     * 返回载荷字节数；文本消息按 UTF-8 编码后计算。
     *
     * @return 载荷字节数
     */
    public int size() {
        return text ? textValue.getBytes(StandardCharsets.UTF_8).length : binaryValue.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Message)) {
            return false;
        }
        Message other = (Message) obj;
        if (this.text != other.text) {
            return false;
        }
        return this.text ? this.textValue.equals(other.textValue) : Arrays.equals(this.binaryValue, other.binaryValue);
    }

    @Override
    public int hashCode() {
        return text ? textValue.hashCode() : Arrays.hashCode(binaryValue);
    }

    @Override
    public String toString() {
        return text ? "Message{text=" + textValue + "}" : "Message{binary=" + binaryValue.length + " bytes}";
    }
}
