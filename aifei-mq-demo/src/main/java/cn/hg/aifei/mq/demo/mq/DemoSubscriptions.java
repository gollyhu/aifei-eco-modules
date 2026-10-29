/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.hg.aifei.mq.MessageListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示侧订阅登记表：demo 内「谁在哪个实例上订阅了哪个主题」的唯一事实来源。
 *
 * <p>登记按「实例 → topic → {@link Entry}」两级组织，一个 topic 至多一条 {@link Entry}，Entry 内再分两个槽：
 * <b>装配期槽</b>（{@code DemoAppConfig.onStart()} 注册，<b>不保存 listener</b>）与<b>本页槽</b>（页面经
 * {@code subscribe} 端点注册，保存 listener 以便 {@code unsubscribe} 按实例身份取回）。装配期槽不保存 listener
 * 是有意为之：页面因此无法取回装配期监听器、也就无法把它取消掉（基线硬约束 7）。</p>
 *
 * <p>线程安全用单一类级锁 {@link #LOCK}——本 demo 的登记量极小，不值得分段锁；读方法一律在锁内做副本返回。</p>
 */
public final class DemoSubscriptions {

    /**
     * 来源标记：装配期（{@code DemoAppConfig.onStart()}）注册。
     */
    public static final String SOURCE_STARTUP = "startup";

    /**
     * 来源标记：本页经 {@code subscribe} 端点注册。
     */
    public static final String SOURCE_PAGE = "page";

    /**
     * 保护 {@link #TABLE} 的唯一锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 实例名 → （topic → 登记项）；保持登记顺序以便接口输出稳定。
     */
    private static final Map<String, LinkedHashMap<String, Entry>> TABLE =
            new LinkedHashMap<String, LinkedHashMap<String, Entry>>();

    /**
     * 禁止创建工具类实例。
     */
    private DemoSubscriptions() {
    }

    /**
     * 登记一条装配期订阅；重复调用幂等，且不会覆盖同 topic 上的本页槽。
     *
     * @param instanceName 实例名
     * @param topic 主题
     * @param subscriberName 订阅者名称
     * @throws IllegalArgumentException 任一参数为空白
     */
    public static void registerStartup(String instanceName, String topic, String subscriberName) {
        requireText(instanceName, "instanceName");
        requireText(topic, "topic");
        requireText(subscriberName, "subscriberName");
        synchronized (LOCK) {
            Entry entry = entryOf(instanceName, topic);
            if (entry.startupSubscriberName == null) {
                entry.startupSubscriberName = subscriberName;
            }
        }
    }

    /**
     * 登记一条本页订阅；同 topic 已有本页槽时返回 {@code false} 且不做任何改动，供端点做幂等判定。
     *
     * @param instanceName 实例名
     * @param topic 主题
     * @param subscriberName 订阅者名称
     * @param listener 本页订阅的监听器
     * @return 本次是否新增了本页槽
     * @throws IllegalArgumentException 任一参数为空白，或 {@code listener} 为 {@code null}
     */
    public static boolean attachPage(String instanceName, String topic, String subscriberName,
                                     MessageListener listener) {
        requireText(instanceName, "instanceName");
        requireText(topic, "topic");
        requireText(subscriberName, "subscriberName");
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        synchronized (LOCK) {
            Entry entry = entryOf(instanceName, topic);
            if (entry.pageListener != null) {
                return false;
            }
            entry.pageSubscriberName = subscriberName;
            entry.pageListener = listener;
            return true;
        }
    }

    /**
     * 查询某实例某主题是否已有本页槽。
     *
     * @param instanceName 实例名
     * @param topic 主题
     * @return 已有本页槽返回 {@code true}
     */
    public static boolean hasPage(String instanceName, String topic) {
        synchronized (LOCK) {
            Entry entry = entryOf(instanceName, topic, false);
            return entry != null && entry.pageListener != null;
        }
    }

    /**
     * 摘除本页槽并取回其监听器；装配期槽不受影响（存在时 Entry 保留，chip 退化为「装配期」）。
     *
     * @param instanceName 实例名
     * @param topic 主题
     * @return 被摘除的本页监听器；无本页槽时返回 {@code null}
     */
    public static MessageListener detachPage(String instanceName, String topic) {
        synchronized (LOCK) {
            Entry entry = entryOf(instanceName, topic, false);
            if (entry == null || entry.pageListener == null) {
                return null;
            }
            MessageListener listener = entry.pageListener;
            entry.pageListener = null;
            entry.pageSubscriberName = null;
            if (entry.startupSubscriberName == null) {
                removeEntry(instanceName, topic);
            }
            return listener;
        }
    }

    /**
     * 返回某实例已登记的 topic 列表（去重，按首次登记顺序）。
     *
     * @param instanceName 实例名
     * @return topic 副本；未登记时为空列表
     */
    public static List<String> topics(String instanceName) {
        synchronized (LOCK) {
            LinkedHashMap<String, Entry> topics = TABLE.get(instanceName);
            if (topics == null || topics.isEmpty()) {
                return new ArrayList<String>();
            }
            return new ArrayList<String>(topics.keySet());
        }
    }

    /**
     * 返回某实例的订阅者明细，同 topic 的两个来源各出一条（先装配期、后本页）。
     *
     * @param instanceName 实例名
     * @return 形如 {@code {topic, name, source}} 的明细副本；未登记时为空列表
     */
    public static List<Map<String, Object>> subscribers(String instanceName) {
        synchronized (LOCK) {
            List<Map<String, Object>> subscribers = new ArrayList<Map<String, Object>>();
            LinkedHashMap<String, Entry> topics = TABLE.get(instanceName);
            if (topics == null) {
                return subscribers;
            }
            for (Entry entry : topics.values()) {
                if (entry.startupSubscriberName != null) {
                    subscribers.add(subscriberOf(entry.topic, entry.startupSubscriberName, SOURCE_STARTUP));
                }
                if (entry.pageSubscriberName != null) {
                    subscribers.add(subscriberOf(entry.topic, entry.pageSubscriberName, SOURCE_PAGE));
                }
            }
            return subscribers;
        }
    }

    /**
     * 返回某实例已登记的 topic 数量。
     *
     * @param instanceName 实例名
     * @return topic 数量
     */
    public static int topicCount(String instanceName) {
        synchronized (LOCK) {
            LinkedHashMap<String, Entry> topics = TABLE.get(instanceName);
            return topics == null ? 0 : topics.size();
        }
    }

    /**
     * 清空某实例的全部登记；其他实例不受影响。仅用于重启演练，页面不暴露该能力。
     *
     * @param instanceName 实例名
     */
    public static void clear(String instanceName) {
        synchronized (LOCK) {
            TABLE.remove(instanceName);
        }
    }

    /**
     * 取登记项，不存在则按需创建。
     *
     * @param instanceName 实例名
     * @param topic 主题
     * @return 登记项；不为 {@code null}
     */
    private static Entry entryOf(String instanceName, String topic) {
        Entry entry = entryOf(instanceName, topic, false);
        if (entry == null) {
            entry = entryOf(instanceName, topic, true);
        }
        return entry;
    }

    /**
     * 取登记项。
     *
     * @param instanceName 实例名
     * @param topic 主题
     * @param create 不存在时是否创建
     * @return 登记项；{@code create=false} 且不存在时返回 {@code null}
     */
    private static Entry entryOf(String instanceName, String topic, boolean create) {
        LinkedHashMap<String, Entry> topics = TABLE.get(instanceName);
        if (topics == null) {
            if (!create) {
                return null;
            }
            topics = new LinkedHashMap<String, Entry>();
            TABLE.put(instanceName, topics);
        }
        Entry entry = topics.get(topic);
        if (entry == null && create) {
            entry = new Entry(topic);
            topics.put(topic, entry);
        }
        return entry;
    }

    /**
     * 移除登记项，并在该实例已无任何 topic 时一并移除实例键。
     */
    private static void removeEntry(String instanceName, String topic) {
        LinkedHashMap<String, Entry> topics = TABLE.get(instanceName);
        if (topics == null) {
            return;
        }
        topics.remove(topic);
        if (topics.isEmpty()) {
            TABLE.remove(instanceName);
        }
    }

    /**
     * 组装一条订阅者明细。
     */
    private static Map<String, Object> subscriberOf(String topic, String name, String source) {
        Map<String, Object> subscriber = new LinkedHashMap<String, Object>();
        subscriber.put("topic", topic);
        subscriber.put("name", name);
        subscriber.put("source", source);
        return subscriber;
    }

    /**
     * 校验必填文本。
     */
    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " can not be blank");
        }
    }

    /**
     * 单个 topic 的登记项：装配期槽不存 listener，本页槽存 listener 供取消订阅时按实例身份取回。
     */
    private static final class Entry {

        private final String topic;

        /**
         * 装配期订阅者名称；{@code null} 表示该 topic 没有装配期订阅。
         */
        private String startupSubscriberName;

        /**
         * 本页订阅者名称；{@code null} 表示该 topic 没有本页订阅。
         */
        private String pageSubscriberName;

        /**
         * 本页订阅的监听器；{@code null} 表示该 topic 没有本页订阅。
         */
        private MessageListener pageListener;

        private Entry(String topic) {
            this.topic = topic;
        }
    }
}
