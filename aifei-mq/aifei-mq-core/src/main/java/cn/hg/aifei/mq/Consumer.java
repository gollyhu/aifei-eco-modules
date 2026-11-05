/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 订阅端：为某个主题登记或取消监听器；{@code unsubscribe} 按实例身份移除（不用 {@code equals}）。
 * 内存后端同一主题按 FIFO 投递，同一主题多个监听器按 {@code subscribe} 登记顺序回调。
 */
public interface Consumer {

    /**
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 空白或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 订阅时所属总线已停止
     * @throws MqException 真实后端订阅主题或启动消费者失败时抛出；内存后端不抛
     */
    void subscribe(String topic, MessageListener listener);

    /**
     * 取消后本进程不再向该监听器派发该主题消息；重复取消、取消从未登记的订阅均为幂等静默 no-op。
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 为空或只含空白、或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 该消费者所属总线已停止
     */
    void unsubscribe(String topic, MessageListener listener);
}
