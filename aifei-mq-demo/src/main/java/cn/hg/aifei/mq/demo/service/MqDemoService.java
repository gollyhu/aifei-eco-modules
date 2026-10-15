/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.service;

import cn.aifei.aop.Inject;
import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.demo.common.arch.OutCode;
import cn.hg.aifei.mq.Consumer;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.demo.common.DemoAppConfig;
import cn.hg.aifei.mq.demo.mq.DemoMessageLog;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 Service：现场验证 aifei-mq 的「装配 → 发送 → 订阅消费」闭环。
 *
 * <p>所有端点均为 GET，返回 {@link Out}（HTTP 恒 200，业务语义由 {@code code} 承载，{@code 0} 为成功）。生产者
 * 由 {@code MqPlugin} 注册为单例，这里按接口类型 {@code @Inject} 取用，即本模块推荐的标准用法；订阅侧收到的
 * 消息由 {@link cn.hg.aifei.mq.demo.mq.DemoMessageListener} 写入 {@link DemoMessageLog}。</p>
 *
 * <p>本模块为同步投递：{@code send} 在生产者调用线程内直接完成回调后才返回，故「发送后立刻查询消息」必定
 * 能看到刚发的那条（这是 {@code delivered} 计数成立的前提）。主题上没有订阅者时视为成功、消息被丢弃但仍
 * 返回发送结果，因此对未订阅主题发送会得到 {@code code=0} 且 {@code delivered=0}。</p>
 */
@Path("/mq/demo")
public class MqDemoService {

    /**
     * 装配入口注册的生产者单例。
     */
    @Inject
    Producer producer;

    /**
     * 装配入口注册的消费者单例；此处仅用于验证装配生效（订阅只在装配期注册一次，见 {@code DemoAppConfig}）。
     */
    @Inject
    Consumer consumer;

    /**
     * 现场验证主入口：返回后端类型、已订阅主题与订阅者、发送 / 接收计数与记录容量。
     *
     * @return 装配与运行态快照
     */
    @Path("state")
    public Out state() {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<String, Object>(DemoMessageLog.startupSnapshot());
            data.put("sentCount", DemoMessageLog.sentCount());
            data.put("receivedCount", DemoMessageLog.receivedCount());
            data.put("messageLogSize", DemoMessageLog.size());
            data.put("messageLogCapacity", DemoMessageLog.MAX_ENTRIES);
            data.put("producerInjected", producer != null);
            data.put("consumerInjected", consumer != null);
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("读取状态失败: " + describe(e));
        }
    }

    /**
     * 发送一条**文本**消息（{@code send(topic, text)} 捷径）：换一个未订阅的主题发送会返回 {@code delivered=0}。
     *
     * @param topic 主题；缺省为演示默认主题
     * @param text 文本载荷；缺省为 {@code hello}
     * @return 发送结果；参数非法为 {@code 10001}，其它异常为 {@code 90000}
     */
    @Path("send")
    public Out send(@Para(name = "topic", defaultValue = DemoAppConfig.DEMO_TOPIC) String topic,
                    @Para(name = "text", defaultValue = "hello") String text) {
        return sendAndReport(null, topic, text);
    }

    /**
     * 发送一条以 {@link Message} 构造的消息（展示对象路径，等价于 {@code send(topic, byte[])}）。
     *
     * @param topic 主题；缺省为演示默认主题
     * @param text 文本载荷；缺省为 {@code hello}
     * @return 发送结果，较 {@link #send(String, String)} 多一项 {@code payloadSize}
     */
    @Path("send/message")
    public Out sendMessage(@Para(name = "topic", defaultValue = DemoAppConfig.DEMO_TOPIC) String topic,
                           @Para(name = "text", defaultValue = "hello") String text) {
        Message message;
        try {
            // Message.of 自带非空校验：空白主题 / 空文本在此即归入参数非法，避免落到框架 20000 兜底
            message = Message.of(topic, text == null ? null : text.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Out.fail(OutCode.INVALID_PARAM).msg("发送参数不合法: " + e.getMessage());
        }
        return sendAndReport(message, topic, text);
    }

    /**
     * 查看订阅侧收到的消息记录（最新在前），是闭环的证据。
     *
     * @return 消息记录
     */
    @Path("messages")
    public Out messages() {
        try {
            List<Map<String, Object>> messages = DemoMessageLog.snapshot();
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("count", messages.size());
            data.put("maxEntries", DemoMessageLog.MAX_ENTRIES);
            data.put("messages", messages);
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("读取消息记录失败: " + describe(e));
        }
    }

    /**
     * 清空演示侧消息记录与累计计数，便于演示前重置现场；订阅与 {@code mq.type} 快照保留
     * （本版无 {@code unsubscribe}，订阅事实无可清）。
     *
     * @return 操作结果
     */
    @Path("messages/clear")
    public Out clearMessages() {
        try {
            DemoMessageLog.clear();
            return Out.ok("消息记录与累计计数已清空（订阅与 mq.type 快照保留）");
        } catch (RuntimeException e) {
            return Out.fail("清空消息记录失败: " + describe(e));
        }
    }

    /**
     * 校验两个注入单例都已就绪；未就绪时返回失败响应，避免抛出空指针。
     *
     * @return 就绪时返回 {@code null}，否则返回失败响应（{@code 90000}）
     */
    private Out requireInjected() {
        if (producer == null || consumer == null) {
            return Out.fail("MQ 尚未装配完成（producer=" + producer + ", consumer=" + consumer
                    + "），请确认 config(Plugins) 已注册 MqPlugin");
        }
        return null;
    }

    /**
     * 发送并汇报本次投递效果：{@code delivered} 为本次发送期间新增的接收记录数（同步投递下即本次订阅者
     * 回调次数）。主题或文本空白（含 {@code null}）返回 {@code 10001}。
     *
     * @param message 已构造的消息；为 {@code null} 时走 {@code send(topic, text)} 文本捷径，
     *                非 {@code null} 时走对象路径并在返回值中附加 {@code payloadSize}
     * @param topic 主题
     * @param text 文本载荷
     * @return 发送结果
     */
    private Out sendAndReport(Message message, String topic, String text) {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        if (topic == null || topic.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("topic 不能为空");
        }
        if (text == null || text.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("text 不能为空");
        }
        long before = DemoMessageLog.receivedCount();
        SendResult result;
        try {
            result = message != null ? producer.send(message) : producer.send(topic, text);
        } catch (RuntimeException e) {
            return Out.fail("发送失败: " + describe(e));
        }
        long receivedCount = DemoMessageLog.receivedCount();
        DemoMessageLog.markSent(topic);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("topic", topic);
        data.put("messageId", result.messageId());
        data.put("text", text);
        data.put("delivered", receivedCount - before);
        data.put("deliveredRefTopic", result.topic());
        data.put("receivedCount", receivedCount);
        if (message != null) {
            data.put("payloadSize", message.payload().length);
        }
        return Out.ok("已发送到主题 " + topic).data(data);
    }

    /**
     * 把运行态异常整理成一行可读描述，便于端点响应中定位问题。
     *
     * @param e 捕获到的异常
     * @return 形如 {@code IllegalStateException: xxx} 的描述
     */
    private String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
