/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;

/**
 * {@link Consumer} 的静态门面：不注入也能取到消费者实例，或用静态方法直接在主实例上订阅与取消订阅。
 * 「主实例」指配置里 {@code mq.*} 那一套，也是 {@code @Inject Consumer} 注入的对象；扩展实例用 {@link #use(String)}
 * 按 {@code mq.names} 声明的实例名取，取到的是启动时装配好的同一对象。
 * 镜像方法只转发，参数与异常契约同 {@link Consumer}，但要先取实例，故应用未启动 / 已停止时抛 {@link IllegalStateException}。
 */
public final class ConsumerKit {

    private ConsumerKit() {
    }

    /**
     * @return 主实例的消费者
     * @throws IllegalStateException 应用尚未启动或已经停止
     */
    public static Consumer use() {
        return use(MqConfig.MAIN_INSTANCE_NAME);
    }

    /**
     * @param instanceName 实例名；实例名在配置项 {@code mq.names} 里声明，主实例用 {@code use()} 无需传名字
     * @return 该实例的消费者
     * @throws IllegalStateException 应用尚未启动或已经停止
     * @throws IllegalArgumentException 实例名为空、含非法字符或未注册
     */
    public static Consumer use(String instanceName) {
        return MqHolder.consumer(instanceName);
    }

    // ─────────── 以下静态方法与 Consumer 的同名方法一一对应，一律作用于主实例 ───────────

    /**
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 空白或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或订阅时所属实例已停止
     * @throws MqException 真实后端订阅主题或启动消费者失败时抛出；内存后端不抛
     */
    public static void subscribe(String topic, MessageListener listener) {
        use().subscribe(topic, listener);
    }

    /**
     * @param topic 主题
     * @param listener 监听器
     * @throws IllegalArgumentException {@code topic} 空白或 {@code listener} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或取消订阅时所属实例已停止
     */
    public static void unsubscribe(String topic, MessageListener listener) {
        use().unsubscribe(topic, listener);
    }
}
