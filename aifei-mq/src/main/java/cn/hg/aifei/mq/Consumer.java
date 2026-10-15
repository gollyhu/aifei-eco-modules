/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 订阅端视图：为某个主题登记监听器。
 *
 * <p>同一主题多个监听器按登记顺序依次接收消息；本版不提供取消订阅。</p>
 */
public interface Consumer {

    /**
     * 订阅指定主题；此后该主题上的每条消息都会回调 {@code listener}。
     *
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 空白或 {@code listener} 为 {@code null}
     */
    void subscribe(String topic, MessageListener listener);
}
