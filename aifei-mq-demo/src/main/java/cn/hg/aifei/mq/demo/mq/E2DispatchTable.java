/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * E2 分发统计的唯一事实来源：按「业务域 → 事件类型」累计命中次数与「最近一次」，并保留每域一条
 * {@code unknown} 兜底桶。
 *
 * <p>统计<b>按实例分片</b>：{@link #of(String)} 取某实例的分片，只被 {@link E2EventListener} 写、被
 * {@code MqDemoService} 读。分片内有状态字段由分片内私锁保护：本模块虽是同步投递（回调在生产者线程内执行），
 * 但 Undertow 请求线程池是多线程的，且换成真实后端后回调必然来自消费线程，故按「回调可能来自任意线程」加锁；
 * 单锁而非原子类，是因为「命中次数 + 最近一次」必须同一临界区内更新，否则前端会看到
 * 「{@code count=1} 但 {@code lastBizId=null}」的撕裂态（与 {@link DemoMessageLog} 同一套心智模型）。</p>
 */
public final class E2DispatchTable {

    /**
     * 兜底桶的类型键；也是命中表里「未知 type」行的 {@code type} 值。
     */
    private static final String UNKNOWN = "unknown";

    /**
     * 最近一次命中的时间格式；与消息流记录同格式，便于对照。
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 实例名到分片的映射；惰性建立，进程内长期复用。
     */
    private static final Map<String, E2DispatchTable> SHARDS =
            new ConcurrentHashMap<String, E2DispatchTable>();

    /**
     * 本分片所属的实例名。
     */
    private final String instanceName;

    /**
     * 保护本分片全部有状态字段的锁。
     */
    private final Object lock = new Object();

    /**
     * 已知域 → （类型键 → 统计）；构造时按 {@link E2Topics} 预置全部类型与 {@code unknown} 桶，故行数恒定。
     */
    private final Map<String, LinkedHashMap<String, Stat>> buckets;

    /**
     * 未知域的防御桶：域 topic → 统计。正常演示不可达（监听器只订阅已知域 topic）。
     */
    private final Map<String, Stat> unknownDomains = new LinkedHashMap<String, Stat>();

    /**
     * 累计接收的事件数；等于本次分片内所有命中（含兜底桶）之和。
     */
    private long totalReceived;

    /**
     * 累计落入兜底桶的事件数：已知域内 type 未知 / 不可解析，以及未知域（防御分支）两类之和。
     */
    private long unknownCount;

    /**
     * 最近一条事件的 payload 原文；无事件时为 {@code null}。
     */
    private String lastPayload;

    /**
     * 禁止外部构造，只能经 {@link #of(String)} 取分片。
     */
    private E2DispatchTable(String instanceName) {
        this.instanceName = instanceName;
        this.buckets = new LinkedHashMap<String, LinkedHashMap<String, Stat>>();
        for (String domain : E2Topics.all()) {
            LinkedHashMap<String, Stat> types = new LinkedHashMap<String, Stat>();
            for (String type : E2Topics.typesOf(domain)) {
                types.put(type, new Stat());
            }
            types.put(UNKNOWN, new Stat());
            buckets.put(domain, types);
        }
    }

    /**
     * 取指定实例的分片；首次取用时惰性建立，此后返回同一对象。
     *
     * @param instanceName 实例名，由 {@link MqInstanceCatalog#resolve(String)} 校验后传入
     * @return 该实例的分片；不为 {@code null}
     * @throws IllegalArgumentException 实例名为 {@code null} 或只含空白（不静默建分片）
     */
    public static E2DispatchTable of(String instanceName) {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            throw new IllegalArgumentException("instanceName can not be blank");
        }
        E2DispatchTable shard = SHARDS.get(instanceName);
        if (shard == null) {
            E2DispatchTable created = new E2DispatchTable(instanceName);
            E2DispatchTable existing = SHARDS.putIfAbsent(instanceName, created);
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
     * 记录一条已分发的事件；由 {@link E2EventListener} 在回调线程上调用。
     *
     * <p>命中判定：{@code domain} 是已知域且 {@code type} 在其类型清单内则计入该行，否则计入该域的
     * {@code unknown} 兜底桶；{@code domain} 不属于任何已知域时计入未知域防御桶。</p>
     *
     * @param domain 消息所属域 topic
     * @param type 解析出的事件类型；无法解析时为 {@code null}
     * @param bizId 解析出的业务字段值；缺失时为 {@code null}
     * @param payload payload 原文；用于 {@code lastPayload}
     */
    public void record(String domain, String type, String bizId, String payload) {
        String key = domain == null ? "" : domain;
        synchronized (lock) {
            totalReceived++;
            lastPayload = payload;
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            LinkedHashMap<String, Stat> types = buckets.get(key);
            if (types == null) {
                statOf(unknownDomains, key).hit(bizId, timestamp);
                unknownCount++;
                return;
            }
            Stat stat = type == null ? null : types.get(type);
            if (stat == null) {
                stat = types.get(UNKNOWN);
                unknownCount++;
            }
            stat.hit(bizId, timestamp);
        }
    }

    /**
     * 返回某域某类型的命中次数。
     *
     * @param domain 业务域 topic
     * @param type 事件类型；{@code null} 视为查兜底桶
     * @return 命中次数；未知域返回 {@code 0}
     */
    public long count(String domain, String type) {
        synchronized (lock) {
            LinkedHashMap<String, Stat> types = buckets.get(domain);
            if (types == null) {
                return 0L;
            }
            Stat stat = types.get(type == null ? UNKNOWN : type);
            return stat == null ? 0L : stat.count;
        }
    }

    /**
     * 返回累计落入兜底桶的事件数。
     *
     * @return 兜底命中数
     */
    public long unknownCount() {
        synchronized (lock) {
            return unknownCount;
        }
    }

    /**
     * 返回累计接收的事件数。
     *
     * @return 接收数
     */
    public long totalReceived() {
        synchronized (lock) {
            return totalReceived;
        }
    }

    /**
     * 返回最近一条事件的 payload 原文。
     *
     * @return payload 原文；无事件时为 {@code null}
     */
    public String lastPayload() {
        synchronized (lock) {
            return lastPayload;
        }
    }

    /**
     * 返回命中表行：已知域按声明顺序逐类型一行、每域末尾一行 {@code unknown}，未知域动态行仅在有命中时追加到最后。
     *
     * @return 形如 {@code {domain, type, count, lastBizId, lastAt}} 的行副本；不为 {@code null}
     */
    public List<Map<String, Object>> rows() {
        synchronized (lock) {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (Map.Entry<String, LinkedHashMap<String, Stat>> domain : buckets.entrySet()) {
                for (Map.Entry<String, Stat> type : domain.getValue().entrySet()) {
                    rows.add(rowOf(domain.getKey(), type.getKey(), type.getValue()));
                }
            }
            for (Map.Entry<String, Stat> domain : unknownDomains.entrySet()) {
                if (domain.getValue().count > 0L) {
                    rows.add(rowOf(domain.getKey(), UNKNOWN, domain.getValue()));
                }
            }
            return rows;
        }
    }

    /**
     * 命中表快照，与 {@link #rows()} 同义（设计口径亦以 {@code snapshot()} 称呼该读方法）。
     *
     * @return 行副本；不为 {@code null}
     */
    public List<Map<String, Object>> snapshot() {
        return rows();
    }

    /**
     * 清空本分片的命中统计与累计计数，便于演示前重置现场；其他实例的分片不受影响。
     */
    public void clear() {
        synchronized (lock) {
            for (LinkedHashMap<String, Stat> types : buckets.values()) {
                for (Stat stat : types.values()) {
                    stat.reset();
                }
            }
            unknownDomains.clear();
            totalReceived = 0L;
            unknownCount = 0L;
            lastPayload = null;
        }
    }

    /**
     * 取统计项，不存在则按需创建。
     *
     * @param stats 统计容器
     * @param key 维度键
     * @return 统计项；不为 {@code null}
     */
    private static Stat statOf(Map<String, Stat> stats, String key) {
        Stat stat = stats.get(key);
        if (stat == null) {
            stat = new Stat();
            stats.put(key, stat);
        }
        return stat;
    }

    /**
     * 组装命中表一行。
     *
     * @param domain 业务域 topic
     * @param type 事件类型
     * @param stat 该维度统计
     * @return 行数据，字段顺序固定
     */
    private static Map<String, Object> rowOf(String domain, String type, Stat stat) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("domain", domain);
        row.put("type", type);
        row.put("count", Long.valueOf(stat.count));
        // 未命中过时保持 null，前端渲染为「—」
        row.put("lastBizId", stat.lastBizId);
        row.put("lastAt", stat.lastAt);
        return row;
    }

    /**
     * 单个维度的统计值：命中次数 + 最近一次的业务字段值与时点。
     */
    private static final class Stat {

        private long count;

        private String lastBizId;

        private String lastAt;

        private void hit(String bizId, String timestamp) {
            count++;
            lastBizId = bizId;
            lastAt = timestamp;
        }

        private void reset() {
            count = 0L;
            lastBizId = null;
            lastAt = null;
        }
    }
}
