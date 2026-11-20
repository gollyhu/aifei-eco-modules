/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.captcha.internal;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认进程内 LRU 存储：访问顺序淘汰 + TTL 到期 + 失败计数上限。
 *
 * <p>全部方法同步，容量与失败上限由构造入参固定；不依赖任何第三方组件。</p>
 */
public final class LruCaptchaStore implements CaptchaStore {

    private final int capacity;
    private final int maxFailures;
    private final LinkedHashMap<String, Entry> entries;

    /**
     * 构造存储。
     *
     * @param capacity LRU 容量，须为正数
     * @param maxFailures 失败次数上限，须为正数
     */
    public LruCaptchaStore(int capacity, int maxFailures) {
        this.capacity = capacity;
        this.maxFailures = maxFailures;
        this.entries = new LinkedHashMap<String, Entry>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > capacity;
            }
        };
    }

    @Override
    public synchronized void save(String token, String answer, long expireAt) {
        entries.put(token, new Entry(answer, expireAt));
    }

    @Override
    public synchronized String consume(String token) {
        Entry entry = entries.get(token);
        if (entry == null) {
            return null;
        }
        if (entry.expired() || entry.failures >= maxFailures) {
            entries.remove(token);
            return null;
        }
        return entry.answer;
    }

    @Override
    public synchronized void fail(String token) {
        Entry entry = entries.get(token);
        if (entry == null) {
            return;
        }
        if (entry.expired()) {
            entries.remove(token);
            return;
        }
        entry.failures++;
        if (entry.failures >= maxFailures) {
            entries.remove(token);
        }
    }

    @Override
    public synchronized void remove(String token) {
        entries.remove(token);
    }

    /**
     * 当前存储条目数（历史遗留过期项由读写路径惰性清理）。
     *
     * @return 条目数
     */
    public synchronized int size() {
        return entries.size();
    }

    private static final class Entry {
        private final String answer;
        private final long expireAt;
        private int failures;

        private Entry(String answer, long expireAt) {
            this.answer = answer;
            this.expireAt = expireAt;
        }

        private boolean expired() {
            return expireAt <= System.currentTimeMillis();
        }
    }
}
