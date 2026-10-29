/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;
import java.nio.charset.StandardCharsets;

/**
 * E2 专用监听器：<b>一个入口 + 本地按 {@code type} 分发</b>，过程只有「解码 → 计入本实例分片」。
 *
 * <p>与 {@link DemoMessageListener} / {@link PageDemoListener} 刻意分离：本类<b>不写</b>
 * {@link DemoMessageLog}，因此 E2 事件不会出现在右列消息流卡、{@code /mq/demo/event/send} 的
 * {@code delivered} 只反映 E2 侧回调，两套演示得以干净对照。</p>
 *
 * <p>回调线程不确定（同步投递在 Undertow 请求线程池，真实后端为消费线程），且总线会就地隔离并静默丢弃监听器
 * 异常，故 {@link #onMessage(Message)} 全路径不抛异常：解析不了的 payload 计入 {@code unknown} 兜底桶。</p>
 */
public class E2EventListener implements MessageListener {

    /**
     * 订阅者名称；由 {@code DemoAppConfig.onStart()} 随装配期订阅登记进 {@link DemoSubscriptions}。
     */
    public static final String NAME = "E2EventListener";

    /**
     * 本实例名，构造时定死；仅作诊断用途。
     */
    private final String instanceName;

    /**
     * 本实例的分发统计分片，构造时定死，回调线程上只写这一处。
     */
    private final E2DispatchTable table;

    /**
     * 创建主实例（{@code __MAIN__}）的监听器。
     */
    public E2EventListener() {
        this(MqInstanceCatalog.MAIN);
    }

    /**
     * 创建指定实例的监听器。
     *
     * @param instanceName 该监听器所订阅的实例名
     * @throws IllegalArgumentException 实例名为空白
     */
    public E2EventListener(String instanceName) {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceName can not be blank");
        }
        this.instanceName = instanceName;
        this.table = E2DispatchTable.of(instanceName);
    }

    /**
     * 返回本监听器所属的实例名。
     *
     * @return 实例名
     */
    public String instanceName() {
        return instanceName;
    }

    /**
     * 接收一条域 topic 上的消息：解码 payload 后按 {@code type} 计入本实例分片。
     *
     * @param message 收到的消息；永不为 {@code null}
     */
    @Override
    public void onMessage(Message message) {
        try {
            // Message 只提供字节 payload，文本需自行按 UTF-8 解码
            String payload = new String(message.payload(), StandardCharsets.UTF_8);
            E2Topics.Event event = E2Topics.decode(payload);
            table.record(message.topic(), event.type(), event.bizId(), payload);
        } catch (RuntimeException e) {
            // 总线会静默丢弃监听器异常：这里不外抛，避免一条坏消息影响后续消息的计数
            System.out.println("[aifei-mq-demo] E2 分发失败(" + instanceName + "): "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
