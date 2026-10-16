/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 订阅端视图：为某个主题登记或取消监听器。
 *
 * <p>{@code unsubscribe} 按<b>实例身份</b>移除该监听器实例（而非按 {@code equals} 判定）。同一主题多个
 * 监听器的回调顺序随后端而变：内存后端按 {@code subscribe} 登记顺序依次回调（§5.6 (B)）。</p>
 */
public interface Consumer {

    /**
     * 订阅指定主题；此后该主题上的每条消息都会回调 {@code listener}。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 空白或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 订阅时所属总线已停止
     * @throws MqException 真实后端订阅主题或启动消费者失败时抛出；内存后端不抛
     */
    void subscribe(String topic, MessageListener listener);

    /**
     * 取消订阅：从该主题的登记列表中移除该 {@code listener} 实例；取消后本进程不再向该监听器派发该主题的消息。
     *
     * <p>重复取消、取消从未登记的订阅均为幂等静默 no-op（不抛异常）。</p>
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 为空或只含空白、或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 该消费者所属总线已停止
     */
    void unsubscribe(String topic, MessageListener listener);
}
