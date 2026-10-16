/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

import cn.hg.aifei.mq.internal.MqConfig;
import cn.hg.aifei.mq.internal.MqHolder;

/**
 * {@link Producer} 的静态工具类：不注入也能取到生产者实例，或用静态方法直接向主实例发送。
 *
 * <p>「主实例」指配置里 {@code mq.*} 那一套，应用启动后就存在，也正是 {@code @Inject Producer} 注入的对象。
 * 配置了扩展实例时，用 {@link #use(String)} 按实例名取；实例名在配置项 {@code mq.names} 里声明，取到的是
 * 应用启动时装配好的同一个对象，可以放心存下来反复使用。</p>
 *
 * <p>下列镜像方法的参数与异常契约同 {@link Producer} 的对应方法，只做转发：不重复校验、不转换异常。
 * 它们都要先取实例，因此与 {@link #use()} 一样，可能在应用尚未启动或已经停止时抛
 * {@link IllegalStateException}。</p>
 */
public final class ProducerUtil {

    /**
     * 禁止创建工具类实例。
     */
    private ProducerUtil() {
    }

    /**
     * 取主实例的生产者。
     *
     * @return 主实例的生产者
     * @throws IllegalStateException 应用尚未启动或已经停止
     */
    public static Producer use() {
        return use(MqConfig.MAIN_INSTANCE_NAME);
    }

    /**
     * 按实例名取生产者实例；实例名在配置项 {@code mq.names} 里声明。
     *
     * @param instanceName 实例名；主实例用 {@code use()}，无需传名字
     * @return 该实例的生产者
     * @throws IllegalStateException 应用尚未启动或已经停止
     * @throws IllegalArgumentException 实例名为空、含非法字符或未注册
     */
    public static Producer use(String instanceName) {
        return MqHolder.producer(instanceName);
    }

    // ─────────── 以下静态方法与 Producer 的同名方法一一对应，一律作用于主实例 ───────────

    /**
     * 发送一条已构造的消息；规则同 {@link Producer#send(Message)}。
     *
     * @param message 消息
     * @return 发送结果
     * @throws IllegalArgumentException {@code message} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或发送时所属实例已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    public static SendResult send(Message message) {
        return use().send(message);
    }

    /**
     * 发送字节载荷；规则同 {@link Producer#send(String, byte[])}。
     *
     * @param topic 主题
     * @param payload 字节载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code payload} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或发送时所属实例已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    public static SendResult send(String topic, byte[] payload) {
        return use().send(topic, payload);
    }

    /**
     * 发送文本载荷；规则同 {@link Producer#send(String, String)}。
     *
     * @param topic 主题
     * @param text 文本载荷
     * @return 发送结果
     * @throws IllegalArgumentException {@code topic} 空白或 {@code text} 为 {@code null}
     * @throws IllegalStateException 应用尚未启动或已经停止，或发送时所属实例已停止
     * @throws MqException 真实后端运行时故障时抛出；内存后端不抛
     */
    public static SendResult send(String topic, String text) {
        return use().send(topic, text);
    }
}
