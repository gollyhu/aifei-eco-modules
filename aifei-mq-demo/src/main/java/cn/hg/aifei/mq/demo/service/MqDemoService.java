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
import cn.hg.aifei.mq.MessageListener;
import cn.hg.aifei.mq.Producer;
import cn.hg.aifei.mq.SendResult;
import cn.hg.aifei.mq.demo.common.DemoAppConfig;
import cn.hg.aifei.mq.demo.mq.DemoMessageLog;
import cn.hg.aifei.mq.demo.mq.DemoSubscriptions;
import cn.hg.aifei.mq.demo.mq.E2DispatchTable;
import cn.hg.aifei.mq.demo.mq.E2Topics;
import cn.hg.aifei.mq.demo.mq.MqInstanceCatalog;
import cn.hg.aifei.mq.demo.mq.PageDemoListener;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 Service：现场验证 aifei-mq 的「装配 → 发送 → 订阅消费」闭环，按<b>实例</b>维度隔离。
 *
 * <p>所有端点均为 GET，返回 {@link Out}（HTTP 恒 200，业务语义由 {@code code} 承载，{@code 0} 为成功）。
 * {@code instance} 参数缺省即主实例 {@code __MAIN__}，故不传参的旧调用行为不变；参数校验、错误码与文案
 * 统一由 {@link MqInstanceCatalog#resolve(String)} 给出，本类只写「校验 → 取数 → 组装」。</p>
 *
 * <p>两个注入字段仅作主实例的装配证据（{@code @Inject} 只覆盖主实例）；多实例操作一律经
 * {@link MqInstanceCatalog.Instance 实例明细} 取生产者 / 消费者，不在此长期持有命名实例的对象。</p>
 *
 * <p>本模块为同步投递：{@code send} 在生产者调用线程内直接完成回调后才返回，故「发送后立刻查询消息」必定
 * 能看到刚发的那条（这是 {@code delivered} 计数成立的前提）。主题上没有订阅者时视为成功、消息被丢弃但仍
 * 返回发送结果，因此对未订阅主题发送会得到 {@code code=0} 且 {@code delivered=0}。</p>
 */
@Path("/mq/demo")
public class MqDemoService {

    /**
     * 装配入口注册的生产者单例（主实例）。
     */
    @Inject
    Producer producer;

    /**
     * 装配入口注册的消费者单例（主实例）；此处仅用于验证装配生效。
     */
    @Inject
    Consumer consumer;

    /**
     * 现场验证主入口：返回该实例的后端类型、已订阅主题与订阅者、发送 / 接收计数与记录容量。
     *
     * @param instance 实例名；缺省为主实例
     * @return 装配与运行态快照
     */
    @Path("state")
    public Out state(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        try {
            DemoMessageLog log = DemoMessageLog.of(it.instanceName());
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", it.instanceName());
            data.put("mqType", it.type());
            data.put("configPrefix", it.configPrefix());
            putSubscriptions(data, it.instanceName());
            data.put("sentCount", log.sentCount());
            data.put("receivedCount", log.receivedCount());
            data.put("messageLogSize", log.size());
            data.put("messageLogCapacity", log.capacity());
            data.put("producerInjected", producer != null);
            data.put("consumerInjected", consumer != null);
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("读取状态失败: " + describe(e));
        }
    }

    /**
     * 发送一条**文本**消息（{@code send(topic, text)} 捷径）：向未订阅的主题发送会返回 {@code delivered=0}。
     *
     * @param instance 实例名；缺省为主实例
     * @param topic 主题；缺省为演示默认主题
     * @param text 文本载荷；缺省为 {@code hello}
     * @return 发送结果；参数非法为 {@code 10001}，其它异常为 {@code 90000}
     */
    @Path("send")
    public Out send(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance,
                    @Para(name = "topic", defaultValue = DemoAppConfig.DEMO_TOPIC) String topic,
                    @Para(name = "text", defaultValue = "hello") String text) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        return sendAndReport(it, null, topic, text);
    }

    /**
     * 发送一条以 {@link Message} 构造的消息（展示对象路径，等价于 {@code send(topic, byte[])}）。
     *
     * @param instance 实例名；缺省为主实例
     * @param topic 主题；缺省为演示默认主题
     * @param text 文本载荷；缺省为 {@code hello}
     * @return 发送结果，较 {@link #send(String, String, String)} 多一项 {@code payloadSize}
     */
    @Path("send/message")
    public Out sendMessage(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance,
                           @Para(name = "topic", defaultValue = DemoAppConfig.DEMO_TOPIC) String topic,
                           @Para(name = "text", defaultValue = "hello") String text) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        Message message;
        try {
            // Message.of 自带非空校验：空白主题 / 空文本在此即归入参数非法，避免落到框架 20000 兜底
            message = Message.of(topic, text == null ? null : text.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Out.fail(OutCode.INVALID_PARAM).msg("发送参数不合法: " + e.getMessage());
        }
        return sendAndReport(it, message, topic, text);
    }

    /**
     * 查看该实例订阅侧收到的消息记录（最新在前），是闭环的证据。
     *
     * @param instance 实例名；缺省为主实例
     * @return 消息记录
     */
    @Path("messages")
    public Out messages(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        try {
            DemoMessageLog log = DemoMessageLog.of(it.instanceName());
            List<Map<String, Object>> messages = log.snapshot();
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", it.instanceName());
            data.put("count", messages.size());
            data.put("maxEntries", log.capacity());
            data.put("messages", messages);
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("读取消息记录失败: " + describe(e));
        }
    }

    /**
     * 清空该实例的消息记录与累计计数，便于演示前重置现场；订阅登记与后端类型不动，其他实例不受影响。
     *
     * @param instance 实例名；缺省为主实例
     * @return 操作结果
     */
    @Path("messages/clear")
    public Out clearMessages(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        try {
            DemoMessageLog log = DemoMessageLog.of(it.instanceName());
            log.clear();
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", it.instanceName());
            data.put("messageLogSize", log.size());
            data.put("sentCount", log.sentCount());
            data.put("receivedCount", log.receivedCount());
            return Out.ok("消息记录与累计计数已清空（订阅与 mq.type 快照保留）").data(data);
        } catch (RuntimeException e) {
            return Out.fail("清空消息记录失败: " + describe(e));
        }
    }

    /**
     * 发送一条 E2 事件：topic 只表达业务域，事件类型放在 payload 内。
     *
     * <p>参数口径与既有 {@code topic} / {@code text} 一致：空串取缺省、仅空白算非法。{@code delivered} 为本次
     * 发送期间 {@link E2DispatchTable#totalReceived()} 的增量（同步投递下即 E2 侧回调次数），{@code hitCount}
     * 为该域该类型的累计命中数。</p>
     *
     * @param instance 实例名；缺省为主实例
     * @param domain 业务域 topic；缺省为 {@link E2Topics#DEFAULT_DOMAIN}
     * @param type 事件类型；缺省为该域首个类型
     * @param bizId 业务字段值；缺省为 {@link E2Topics#DEFAULT_BIZ_ID}
     * @return 发送结果；参数非法为 {@code 10001}，其它异常为 {@code 90000}
     */
    @Path("event/send")
    public Out eventSend(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance,
                         @Para(name = "domain", defaultValue = E2Topics.DEFAULT_DOMAIN) String domain,
                         @Para(name = "type", defaultValue = "") String type,
                         @Para(name = "bizId", defaultValue = E2Topics.DEFAULT_BIZ_ID) String bizId) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        String domainName = isEmpty(domain) ? E2Topics.DEFAULT_DOMAIN : domain;
        if (domainName.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("domain 不能为空");
        }
        if (!E2Topics.isDomain(domainName)) {
            return Out.fail(OutCode.INVALID_PARAM)
                    .msg("未知业务域: " + domainName + "，可用业务域: " + E2Topics.all());
        }
        List<String> types = E2Topics.typesOf(domainName);
        String typeName = isEmpty(type) ? E2Topics.defaultTypeOf(domainName) : type;
        if (typeName.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("type 不能为空");
        }
        if (!types.contains(typeName)) {
            return Out.fail(OutCode.INVALID_PARAM)
                    .msg("未知事件类型: " + typeName + "，可用类型(" + domainName + "): " + types);
        }
        String bizIdValue = isEmpty(bizId) ? E2Topics.DEFAULT_BIZ_ID : bizId;
        if (bizIdValue.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("bizId 不能为空");
        }
        String payload = E2Topics.buildPayload(typeName, bizIdValue);
        E2DispatchTable table = E2DispatchTable.of(it.instanceName());
        long before = table.totalReceived();
        SendResult result;
        try {
            result = it.producer().send(domainName, payload);
        } catch (RuntimeException e) {
            return Out.fail("发送事件失败: " + describe(e));
        }
        long totalReceived = table.totalReceived();

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("instance", it.instanceName());
        data.put("domain", domainName);
        data.put("type", typeName);
        data.put("bizId", bizIdValue);
        data.put("version", E2Topics.VERSION);
        data.put("payload", payload);
        data.put("payloadSize", payload.getBytes(StandardCharsets.UTF_8).length);
        data.put("messageId", result.messageId());
        data.put("delivered", totalReceived - before);
        data.put("totalReceived", totalReceived);
        data.put("hitCount", table.count(domainName, typeName));
        return Out.ok("已发送事件 " + typeName + " 到 " + domainName).data(data);
    }

    /**
     * 读取该实例的 E2 分发命中表：域 / 类型候选、累计计数与逐行命中明细（恒含每域 {@code unknown} 兜底行）。
     *
     * @param instance 实例名；缺省为主实例
     * @return 分发统计；参数非法为 {@code 10001}，其它异常为 {@code 90000}
     */
    @Path("event/dispatch")
    public Out eventDispatch(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        try {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", it.instanceName());
            putDispatch(data, E2DispatchTable.of(it.instanceName()));
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("读取分发统计失败: " + describe(e));
        }
    }

    /**
     * 清空该实例的 E2 命中统计，便于演示前重置现场；装配期订阅（不可取消）与消息记录均不受影响。
     *
     * @param instance 实例名；缺省为主实例
     * @return 清空后的分发统计（全 0）；参数非法为 {@code 10001}，其它异常为 {@code 90000}
     */
    @Path("event/dispatch/clear")
    public Out clearEventDispatch(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        try {
            E2DispatchTable table = E2DispatchTable.of(it.instanceName());
            table.clear();
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", it.instanceName());
            putDispatch(data, table);
            return Out.ok(it.instanceName() + " 的事件分发命中统计已清空（订阅保留）").data(data);
        } catch (RuntimeException e) {
            return Out.fail("清空分发统计失败: " + describe(e));
        }
    }

    /**
     * 返回全部 MQ 实例清单：主实例在首，其后按 {@code mq.names} 顺序；未装配的实例只体现为 {@code ready=false}。
     *
     * @return 实例清单
     */
    @Path("instances")
    public Out instances() {
        try {
            List<MqInstanceCatalog.Instance> instances = MqInstanceCatalog.list();
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>(instances.size());
            for (MqInstanceCatalog.Instance it : instances) {
                DemoMessageLog log = DemoMessageLog.of(it.instanceName());
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("instanceName", it.instanceName());
                item.put("type", it.type());
                item.put("main", it.main());
                item.put("configPrefix", it.configPrefix());
                item.put("ready", it.ready());
                item.put("subscribedTopicCount", DemoSubscriptions.topicCount(it.instanceName()));
                item.put("messageLogSize", log.size());
                item.put("messageLogCapacity", log.capacity());
                items.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("count", items.size());
            data.put("instances", items);
            if (MqInstanceCatalog.configError() != null) {
                data.put("configError", MqInstanceCatalog.configError());
            }
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("读取实例清单失败: " + describe(e));
        }
    }

    /**
     * 在本页为某主题登记订阅；同实例同主题的重复订阅是幂等的（不再调库，避免同一条消息被记录多次）。
     *
     * @param instance 实例名；缺省为主实例
     * @param topic 主题；必填
     * @return 订阅后的订阅事实；无本页槽可登记之外的情形见 {@link MqInstanceCatalog}
     */
    @Path("subscribe")
    public Out subscribe(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance,
                         @Para(name = "topic", defaultValue = "") String topic) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        if (topic == null || topic.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("topic 不能为空");
        }
        String instanceName = it.instanceName();
        if (DemoSubscriptions.hasPage(instanceName, topic)) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("instance", instanceName);
            data.put("topic", topic);
            data.put("alreadySubscribed", true);
            putSubscriptions(data, instanceName);
            return Out.ok("该主题已订阅（本页）").data(data);
        }
        MessageListener listener = new PageDemoListener(instanceName);
        try {
            it.consumer().subscribe(topic, listener);
        } catch (RuntimeException e) {
            return Out.fail("订阅失败: " + describe(e));
        }
        DemoSubscriptions.attachPage(instanceName, topic, PageDemoListener.NAME, listener);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("instance", instanceName);
        data.put("topic", topic);
        data.put("alreadySubscribed", false);
        putSubscriptions(data, instanceName);
        return Out.ok("已订阅 " + topic + "（影响该实例的所有浏览器会话）").data(data);
    }

    /**
     * 取消本页为某主题登记的订阅；监听器只从 {@link DemoSubscriptions} 取回，装配期订阅无法由此取消。
     *
     * @param instance 实例名；缺省为主实例
     * @param topic 主题；必填
     * @return 取消后的订阅事实；无本页槽时返回 {@code 10001}
     */
    @Path("unsubscribe")
    public Out unsubscribe(@Para(name = "instance", defaultValue = MqInstanceCatalog.MAIN) String instance,
                           @Para(name = "topic", defaultValue = "") String topic) {
        MqInstanceCatalog.Instance it = MqInstanceCatalog.resolve(instance);
        if (!it.ok()) {
            return it.toOut();
        }
        if (topic == null || topic.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("topic 不能为空");
        }
        String instanceName = it.instanceName();
        MessageListener listener = DemoSubscriptions.detachPage(instanceName, topic);
        if (listener == null) {
            return Out.fail(OutCode.INVALID_PARAM).msg("未登记该主题的页面订阅: " + topic);
        }
        try {
            it.consumer().unsubscribe(topic, listener);
        } catch (RuntimeException e) {
            return Out.fail("取消订阅失败: " + describe(e));
        }
        String msg = "已取消订阅 " + topic + "（影响该实例的所有浏览器会话）";
        if (DemoSubscriptions.topics(instanceName).contains(topic)) {
            msg = msg + "；代码：装配期订阅仍生效";
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("instance", instanceName);
        data.put("topic", topic);
        putSubscriptions(data, instanceName);
        return Out.ok(msg).data(data);
    }

    /**
     * 演示页视图：{@code instance} 只由前端从 URL 读取，服务端不参与渲染。
     *
     * @return 模板名；模板根为 {@code webapp/view}，故必须以 {@code /} 开头
     */
    @Path("page")
    public String page() {
        return "/mq/demo.html";
    }

    /**
     * 发送并汇报本次投递效果：{@code delivered} 为本次发送期间本实例新增的接收记录数（同步投递下即本次订阅者
     * 回调次数）。主题或文本空白（含 {@code null}）返回 {@code 10001}。
     *
     * @param it 已校验可用的实例明细
     * @param message 已构造的消息；为 {@code null} 时走 {@code send(topic, text)} 文本捷径，
     *                非 {@code null} 时走对象路径并在返回值中附加 {@code payloadSize}
     * @param topic 主题
     * @param text 文本载荷
     * @return 发送结果
     */
    private Out sendAndReport(MqInstanceCatalog.Instance it, Message message, String topic, String text) {
        if (topic == null || topic.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("topic 不能为空");
        }
        if (text == null || text.trim().isEmpty()) {
            return Out.fail(OutCode.INVALID_PARAM).msg("text 不能为空");
        }
        DemoMessageLog log = DemoMessageLog.of(it.instanceName());
        long before = log.receivedCount();
        SendResult result;
        try {
            result = message != null ? it.producer().send(message) : it.producer().send(topic, text);
        } catch (RuntimeException e) {
            return Out.fail("发送失败: " + describe(e));
        }
        long receivedCount = log.receivedCount();
        log.markSent(topic);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("instance", it.instanceName());
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
     * 把某实例的订阅事实（主题清单、主题数、订阅者明细）写入响应数据；三处端点共用，避免各算一套。
     *
     * @param data 响应数据
     * @param instanceName 实例名
     */
    private void putSubscriptions(Map<String, Object> data, String instanceName) {
        List<String> topics = DemoSubscriptions.topics(instanceName);
        data.put("subscribedTopics", topics);
        data.put("subscribedTopicCount", topics.size());
        data.put("subscribers", DemoSubscriptions.subscribers(instanceName));
    }

    /**
     * 把某实例的 E2 分发统计写入响应数据；{@code event/dispatch} 与 {@code event/dispatch/clear} 共用，
     * 保证两处字段名与顺序只有一个定义点。
     *
     * @param data 响应数据
     * @param table 该实例的分发统计分片
     */
    private void putDispatch(Map<String, Object> data, E2DispatchTable table) {
        List<String> domains = E2Topics.all();
        Map<String, List<String>> typeOptions = new LinkedHashMap<String, List<String>>();
        for (String domain : domains) {
            typeOptions.put(domain, E2Topics.typesOf(domain));
        }
        List<Map<String, Object>> rows = table.rows();
        data.put("domainTopics", domains);
        data.put("typeOptions", typeOptions);
        data.put("defaultDomain", E2Topics.DEFAULT_DOMAIN);
        data.put("defaultType", E2Topics.defaultTypeOf(E2Topics.DEFAULT_DOMAIN));
        data.put("totalReceived", table.totalReceived());
        data.put("unknownCount", table.unknownCount());
        data.put("lastPayload", table.lastPayload());
        data.put("rowCount", rows.size());
        data.put("rows", rows);
    }

    /**
     * 判断参数是否为「空串」；{@code @Para} 已把省略参数折叠为缺省值，故这里只需判 {@code null} 与空串。
     *
     * @param value 参数值
     * @return 为 {@code null} 或空串返回 {@code true}
     */
    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * 把运行期异常整理成一行可读描述，便于端点响应中定位问题。
     *
     * @param e 捕获到的异常
     * @return 形如 {@code IllegalStateException: xxx} 的描述
     */
    private String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
