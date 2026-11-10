/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 发送端：把消息投递到主题。
 * 内存后端为<b>同步投递</b>（{@code send} 返回前订阅者回调已执行完毕），真实后端为<b>异步生产 + 拉取消费</b>，
 * 返回 {@link SendResult} 不等于已被消费。无订阅者时 {@code send} 仍成功返回 {@link SendResult}（共同底线），
 * 消息被丢弃还是留存属后端差异。详见 {@code docs/design.md} §5.6。
 */
public interface Producer {

    /**
     * @param message 消息
     * @return 发送结果
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     * @throws IllegalStateException 发送时所属总线已停止
     * @throws MqException 真实后端运行时故障（如 broker 不可达、发送未成功）时抛出；内存后端不抛
     */
    SendResult send(Message message);

    /**
     * @param topic 主题
     * @param payload 字节载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     * @throws IllegalStateException 发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    SendResult send(String topic, byte[] payload);

    /**
     * @param topic 主题
     * @param text 文本载荷，以 UTF-8 编码为字节后发送
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code text} 为 {@code null}
     * @throws IllegalStateException 发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    SendResult send(String topic, String text);
}
