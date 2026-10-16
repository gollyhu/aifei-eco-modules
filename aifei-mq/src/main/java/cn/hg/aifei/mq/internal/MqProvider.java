/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

/**
 * 一种消息后端的装配契约：识别自身类型，并按配置创建总线。非承诺 API，仅供装配与实现使用。
 *
 * <p>实现必须无状态：不得声明实例字段保存解析结果，配置在 {@link #createBus(MqConfig)} 内经参数现读
 * 现用，可被多线程复用。</p>
 */
public interface MqProvider {

    /**
     * 后端类型标识，对应配置项 {@code mq.type} 的取值，如 {@code "memory"}。
     *
     * @return 后端类型
     */
    String type();

    /**
     * 按配置创建该后端的总线。
     *
     * @param config 配置对象
     * @return 消息总线
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     * @throws cn.hg.aifei.mq.MqException 真实后端构建总线失败（如必填配置缺失、客户端启动失败）时抛出；构建总线
     *         <b>不校验</b>后端连通性（如 RocketMQ nameserver 不可达不在此抛）；内存后端不抛
     */
    MessageBus createBus(MqConfig config);
}
