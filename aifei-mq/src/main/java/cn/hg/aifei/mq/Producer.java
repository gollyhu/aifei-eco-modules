/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 发送端视图：把消息投递到主题。
 *
 * <p>投递语义随后端而变（差异见 §5.6 (B)）：内存后端为<b>同步投递</b>——{@code send} 在调用线程内完成投递
 * 并返回，返回前订阅者回调已执行完毕；真实后端为<b>异步生产 + 拉取消费</b>——{@code send} 只表示消息已
 * 提交到 broker，返回 {@link SendResult} 不等于已被消费，回调发生在后端自有线程。</p>
 *
 * <p>「无订阅者」须区分两层语义：<b>无订阅者时 {@code send} 仍成功返回 {@link SendResult}（含 {@code messageId}）</b>
 * 属跨后端<b>共同底线</b>（§5.6 A5）；而消息<b>被丢弃还是留存</b>随后端而变（§5.6 B6）——内存后端丢弃，
 * 真实后端由 broker 独立存储，待消费者上线后消费。</p>
 */
public interface Producer {

    /**
     * 发送一条已构造的消息。
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     * @throws IllegalStateException 发送时所属总线已停止
     * @throws MqException 真实后端运行时故障（如 broker 不可达、发送未成功）时抛出；内存后端不抛
     */
    SendResult send(Message message);

    /**
     * 发送字节载荷；主题与载荷构成一条消息。
     *
     * @param topic 主题
     * @param payload 字节载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     * @throws IllegalStateException 发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    SendResult send(String topic, byte[] payload);

    /**
     * 发送文本载荷；文本以 UTF-8 编码为字节后发送。
     *
     * @param topic 主题
     * @param text 文本载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code text} 为 {@code null}
     * @throws IllegalStateException 发送时所属总线已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    SendResult send(String topic, String text);
}
