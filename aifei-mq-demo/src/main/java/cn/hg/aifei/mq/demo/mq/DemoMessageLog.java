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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示用的进程内消息记录：订阅侧的监听器把收到的消息写进来，由 {@code /mq/demo/*} 端点读出去，
 * 便于现场肉眼验证「发送 → 订阅消费」闭环。
 *
 * <p>记录<b>按实例分片</b>：{@link #of(String)} 取某个实例的分片，每个分片独立保存记录与收发计数、各有
 * {@link #MAX_ENTRIES} 条上限，超出后丢弃最旧一条，避免长时间演示下无界增长。分片对象自身的有状态字段由
 * 分片内的私锁保护：本模块虽为同步投递（回调在生产者线程内直接执行），但 Undertow 的请求线程池是多线程的，
 * 两个并发请求会各自在自己的线程里触发回调，且换成真实后端后回调必然来自消费线程，因此必须按「回调可能来自
 * 任意线程」来加锁。</p>
 *
 * <p>本类只负责「消息记录与收发计数」。订阅事实见 {@link DemoSubscriptions}，后端类型与就绪状态见
 * {@link MqInstanceCatalog}。</p>
 */
public final class DemoMessageLog {

    /**
     * 每个实例的消息记录条数上限；超出后丢弃最旧一条。
     */
    public static final int MAX_ENTRIES = 200;

    /**
     * 记录时间戳格式。
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 实例名到分片的映射；惰性建立，进程内长期复用。
     */
    private static final Map<String, DemoMessageLog> SHARDS = new ConcurrentHashMap<String, DemoMessageLog>();

    /**
     * 本分片所属的实例名。
     */
    private final String instanceName;

    /**
     * 保护 {@link #entries} 与两个计数的分片内锁。
     */
    private final Object lock = new Object();

    /**
     * 消息记录，按接收顺序保存；受 {@link #lock} 保护。
     */
    private final Deque<Map<String, Object>> entries = new ArrayDeque<Map<String, Object>>();

    /**
     * 累计发送次数，由 {@link #markSent(String)} 递增。
     */
    private long sentCount;

    /**
     * 累计接收次数，由 {@link #record(String, Message)} 递增，并作为记录的 {@code sequence}。
     */
    private long receivedCount;

    /**
     * 禁止外部构造，只能经 {@link #of(String)} 取分片。
     */
    private DemoMessageLog(String instanceName) {
        this.instanceName = instanceName;
    }

    /**
     * 取指定实例的消息记录分片；首次取用时惰性建立，此后返回同一对象。
     *
     * @param instanceName 实例名，由 {@link MqInstanceCatalog#resolve(String)} 校验后传入
     * @return 该实例的分片；不为 {@code null}
     * @throws IllegalArgumentException 实例名为 {@code null} 或只含空白（不静默建分片）
     */
    public static DemoMessageLog of(String instanceName) {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceName can not be blank");
        }
        DemoMessageLog shard = SHARDS.get(instanceName);
        if (shard == null) {
            DemoMessageLog created = new DemoMessageLog(instanceName);
            DemoMessageLog existing = SHARDS.putIfAbsent(instanceName, created);
            shard = existing != null ? existing : created;
        }
        return shard;
    }

    /**
     * 返回本分片所属的实例名。
     *
     * @return 实例名
     */
    public String instanceName() {
        return instanceName;
    }

    /**
     * 返回记录条数上限；每实例独立计。
     *
     * @return 上限值，恒为 {@link #MAX_ENTRIES}
     */
    public int capacity() {
        return MAX_ENTRIES;
    }

    /**
     * 追加一条消息记录，并把累计接收计数加一；由监听器在回调线程上调用。
     *
     * @param subscriberName 收到消息的订阅者名称
     * @param message 收到的消息
     */
    public void record(String subscriberName, Message message) {
        synchronized (lock) {
            receivedCount++;
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("sequence", receivedCount);
            entry.put("topic", message.topic());
            entry.put("subscriber", subscriberName);
            entry.put("text", new String(message.payload(), StandardCharsets.UTF_8));
            entry.put("payloadSize", message.payload().length);
            entry.put("receivedAt", LocalDateTime.now().format(TIMESTAMP_FORMAT));
            if (entries.size() >= MAX_ENTRIES) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
    }

    /**
     * 累计发送次数加一；由发送端点调用。
     *
     * @param topic 本次发送的主题（当前仅作记录维度保留，用于后续按主题统计）
     */
    public void markSent(String topic) {
        synchronized (lock) {
            sentCount++;
        }
    }

    /**
     * 返回消息记录快照，最新在前。
     *
     * @return 记录副本；不暴露内部集合，不为 {@code null}
     */
    public List<Map<String, Object>> snapshot() {
        synchronized (lock) {
            List<Map<String, Object>> copy = new ArrayList<Map<String, Object>>(entries.size());
            Iterator<Map<String, Object>> iterator = entries.descendingIterator();
            while (iterator.hasNext()) {
                copy.add(iterator.next());
            }
            return copy;
        }
    }

    /**
     * 返回本分片当前消息记录条数。
     *
     * @return 条数，最大为 {@link #MAX_ENTRIES}
     */
    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /**
     * 返回本分片累计接收次数。
     *
     * @return 接收次数
     */
    public long receivedCount() {
        synchronized (lock) {
            return receivedCount;
        }
    }

    /**
     * 返回本分片累计发送次数。
     *
     * @return 发送次数
     */
    public long sentCount() {
        synchronized (lock) {
            return sentCount;
        }
    }

    /**
     * 清空本分片的记录与两个累计计数，便于演示前重置现场；其他实例的分片不受影响。
     */
    public void clear() {
        synchronized (lock) {
            entries.clear();
            sentCount = 0L;
            receivedCount = 0L;
        }
    }
}
