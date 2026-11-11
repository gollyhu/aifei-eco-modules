/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.echo;

import cn.aifei.util.StrUtil;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回环 echo 端点的调用计数（进程内内存，无持久化）。
 *
 * <p>只服务于「前 N 次返回 5xx、第 N+1 次成功」这一种可控行为：按调用方给的 {@code key} 分组计数，
 * 以便同一个页面上「默认不重试」与「显式开重试」两路演示互不干扰。</p>
 *
 * <p>计数器自带自愈：当同一 key 的 {@code failFirst} 取值变化时自动从 0 重新计数，避免上一次演示的残值
 * 让下一次演示「一开始就已经成功」。key 数量有上限，超限淘汰最早插入的分组，防止被随意 key 打爆内存。</p>
 */
public final class EchoState {

    /**
     * 计数器保留的最大分组数。
     */
    private static final int MAX_GROUPS = 32;

    /**
     * 缺省分组名。
     */
    public static final String DEFAULT_KEY = "default";

    /**
     * key → 计数器；用 LinkedHashMap 保持插入顺序以便淘汰最早分组。所有访问都在类锁内。
     */
    private static final Map<String, Counter> COUNTERS = new LinkedHashMap<String, Counter>();

    private EchoState() {
    }

    /**
     * 记一次调用并返回本次是本分组第几次调用（从 1 起）；{@code failFirst} 变化即重置计数。
     *
     * @param key 分组名，空白归一为 {@link #DEFAULT_KEY}
     * @param failFirst 本次演示约定的「前 N 次失败」中的 N
     * @return 本次调用序号，从 1 起
     */
    public static synchronized int hit(String key, int failFirst) {
        String k = normalize(key);
        Counter counter = COUNTERS.get(k);
        if (counter == null) {
            if (COUNTERS.size() >= MAX_GROUPS) {
                Iterator<String> it = COUNTERS.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            counter = new Counter(failFirst);
            COUNTERS.put(k, counter);
        } else if (counter.failFirst != failFirst) {
            counter.failFirst = failFirst;
            counter.count = 0;
        }
        counter.count++;
        return counter.count;
    }

    /**
     * 清空某个分组的计数（下一次调用重新从 1 起）。
     *
     * @param key 分组名，空白归一为 {@link #DEFAULT_KEY}
     * @return 被清理前的计数；该分组不存在返回 0
     */
    public static synchronized int reset(String key) {
        Counter removed = COUNTERS.remove(normalize(key));
        return removed == null ? 0 : removed.count;
    }

    /**
     * 清空全部分组并返回清理前的快照（用于页面「重置 echo 计数」）。
     *
     * @return 清理前的计数器快照（key → 调用次数）
     */
    public static synchronized Map<String, Object> resetAll() {
        Map<String, Object> before = snapshot();
        COUNTERS.clear();
        return before;
    }

    /**
     * 当前计数器快照。
     *
     * @return key → 调用次数（拷贝，可安全序列化）
     */
    public static synchronized Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Counter> e : COUNTERS.entrySet()) {
            data.put(e.getKey(), e.getValue().count);
        }
        return data;
    }

    private static String normalize(String key) {
        return StrUtil.isBlank(key) ? DEFAULT_KEY : key.trim();
    }

    /**
     * 单个分组的计数状态。
     */
    private static final class Counter {

        private int count;

        private int failFirst;

        private Counter(int failFirst) {
            this.failFirst = failFirst;
        }
    }
}
