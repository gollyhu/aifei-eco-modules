/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 发送端视图：把消息投递到主题。
 *
 * <p>投递为同步语义：{@code send} 在调用线程内完成投递并返回，返回前订阅者回调已执行完毕。主题上没有
 * 订阅者时视为成功，消息被丢弃但仍返回发送结果。</p>
 */
public interface Producer {

    /**
     * 发送一条已构造的消息。
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     */
    SendResult send(Message message);

    /**
     * 发送字节载荷；主题与载荷构成一条消息。
     *
     * @param topic 主题
     * @param payload 字节载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     */
    SendResult send(String topic, byte[] payload);

    /**
     * 发送文本载荷；文本以 UTF-8 编码为字节后发送。
     *
     * @param topic 主题
     * @param text 文本载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code text} 为 {@code null}
     */
    SendResult send(String topic, String text);
}
