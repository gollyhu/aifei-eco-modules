/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.hg.aifei.mq.Message;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示用的进程内消息记录：订阅侧的监听器把收到的消息写进来，由 {@code /mq/demo/*} 端点读出去，
 * 便于现场肉眼验证「发送 → 订阅消费」闭环。
 *
 * <p>记录有固定上限（{@link #MAX_ENTRIES}），超出后丢弃最旧一条，避免长时间演示下无界增长。全部状态由
 * {@link #LOCK} 保护：虽然本模块为同步投递（回调在生产者线程内直接执行），但 Undertow 的请求线程池是
 * 多线程的、两个并发请求会各自在自己的线程里触发回调，且未来换成真实后端后回调必然来自消费线程，
 * 因此这里必须按「回调可能来自任意线程」来加锁。</p>
 */
public final class DemoMessageLog {

    /**
     * 消息记录条数上限；超出后丢弃最旧一条。
     */
    public static final int MAX_ENTRIES = 200;

    /**
     * 记录时间戳格式。
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 保护本类全部可变状态的唯一锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 消息记录，按接收顺序保存；受 {@link #LOCK} 保护。
     */
    private static final Deque<Map<String, Object>> ENTRIES = new ArrayDeque<Map<String, Object>>();

    /**
     * 装配快照：已订阅主题；只在 {@link #markStartup(String, String, String)} 时写入一次。
     */
    private static final List<String> SUBSCRIBED_TOPICS = new ArrayList<String>();

    /**
     * 装配快照：订阅者名称，与 {@link #SUBSCRIBED_TOPICS} 按下标一一对应。
     */
    private static final List<String> SUBSCRIBER_NAMES = new ArrayList<String>();

    /**
     * 装配快照：装配期读到的 {@code mq.type} 配置值。
     */
    private static String mqType;

    /**
     * 累计发送次数，由 {@link #markSent(String)} 递增。
     */
    private static long sentCount;

    /**
     * 累计接收次数，由 {@link #record(String, Message)} 递增，并作为记录的 {@code sequence}。
     */
    private static long receivedCount;

    private DemoMessageLog() {
    }

    /**
     * 记录装配快照；只由 {@code DemoAppConfig.onStart()} 调用一次。订阅事实不可被 {@link #clear()} 抹去，
     * 故快照与消息记录分开存放。
     *
     * @param mqType 装配期读到的 {@code mq.type} 配置值
     * @param topic 已订阅主题
     * @param subscriberName 订阅者名称
     */
    public static void markStartup(String mqType, String topic, String subscriberName) {
        synchronized (LOCK) {
            DemoMessageLog.mqType = mqType;
            SUBSCRIBED_TOPICS.clear();
            SUBSCRIBED_TOPICS.add(topic);
            SUBSCRIBER_NAMES.clear();
            SUBSCRIBER_NAMES.add(subscriberName);
        }
    }

    /**
     * 追加一条消息记录，并把累计接收计数加一；由监听器在回调线程上调用。
     *
     * @param subscriberName 收到消息的订阅者名称
     * @param message 收到的消息
     */
    public static void record(String subscriberName, Message message) {
        synchronized (LOCK) {
            receivedCount++;
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("sequence", receivedCount);
            entry.put("topic", message.topic());
            entry.put("subscriber", subscriberName);
            entry.put("text", new String(message.payload(), StandardCharsets.UTF_8));
            entry.put("payloadSize", message.payload().length);
            entry.put("receivedAt", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.removeFirst();
            }
            ENTRIES.addLast(entry);
        }
    }

    /**
     * 累计发送次数加一；由发送端点调用。
     *
     * @param topic 本次发送的主题（当前仅作记录维度保留，用于后续按主题统计）
     */
    public static void markSent(String topic) {
        synchronized (LOCK) {
            sentCount++;
        }
    }

    /**
     * 返回消息记录快照，最新在前。
     *
     * @return 记录副本；不暴露内部集合，不为 {@code null}
     */
    public static List<Map<String, Object>> snapshot() {
        synchronized (LOCK) {
            List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>(ENTRIES.size());
            Iterator<Map<String, Object>> iterator = ENTRIES.descendingIterator();
            while (iterator.hasNext()) {
                copy.add(iterator.next());
            }
            return copy;
        }
    }

    /**
     * 返回当前消息记录条数。
     *
     * @return 条数，最大为 {@link #MAX_ENTRIES}
     */
    public static int size() {
        synchronized (LOCK) {
            return ENTRIES.size();
        }
    }

    /**
     * 返回累计接收次数。
     *
     * @return 接收次数
     */
    public static long receivedCount() {
        synchronized (LOCK) {
            return receivedCount;
        }
    }

    /**
     * 返回累计发送次数。
     *
     * @return 发送次数
     */
    public static long sentCount() {
        synchronized (LOCK) {
            return sentCount;
        }
    }

    /**
     * 返回装配快照：后端类型、已订阅主题、订阅者（含所属主题）与记录容量。
     *
     * @return 装配快照；字段均在 {@link #markStartup(String, String, String)} 时写入
     */
    public static Map<String, Object> startupSnapshot() {
        synchronized (LOCK) {
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("mqType", mqType);
            data.put("subscribedTopics", new ArrayList<String>(SUBSCRIBED_TOPICS));
            List<Map<String, Object>> subscribers = new ArrayList<Map<String, Object>>(SUBSCRIBER_NAMES.size());
            for (int i = 0; i < SUBSCRIBER_NAMES.size(); i++) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("topic", i < SUBSCRIBED_TOPICS.size() ? SUBSCRIBED_TOPICS.get(i) : null);
                item.put("name", SUBSCRIBER_NAMES.get(i));
                subscribers.add(item);
            }
            data.put("subscribers", subscribers);
            data.put("maxEntries", MAX_ENTRIES);
            return data;
        }
    }

    /**
     * 清空消息记录与两个累计计数，便于演示前重置现场；装配快照（订阅与 {@code mq.type}）保留——本方法
     * 只重置 demo 侧记录，不触碰订阅登记（该取舍是 demo 的选择：{@code Consumer.unsubscribe} 现已提供，本 demo 未使用）。
     */
    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
            sentCount = 0L;
            receivedCount = 0L;
        }
    }
}
