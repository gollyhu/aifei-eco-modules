/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.feed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 落点计数：交付总数 + 三个落点各自被调用次数 + mq 消费次数。
 */
public final class SinkCounters {

    private static final SinkCounters INSTANCE = new SinkCounters();

    private final AtomicInteger delivered = new AtomicInteger();

    private final AtomicInteger memory = new AtomicInteger();

    private final AtomicInteger console = new AtomicInteger();

    private final AtomicInteger mq = new AtomicInteger();

    private final AtomicInteger mqConsumed = new AtomicInteger();

    private SinkCounters() {
    }

    /**
     * @return 单例
     */
    public static SinkCounters get() {
        return INSTANCE;
    }

    public void delivered() {
        delivered.incrementAndGet();
    }

    public void memory() {
        memory.incrementAndGet();
    }

    public void console() {
        console.incrementAndGet();
    }

    public void mq() {
        mq.incrementAndGet();
    }

    public void mqConsumed() {
        mqConsumed.incrementAndGet();
    }

    /**
     * @return 计数快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("delivered", delivered.get());
        data.put("memory", memory.get());
        data.put("console", console.get());
        data.put("mq", mq.get());
        data.put("mqConsumed", mqConsumed.get());
        return data;
    }

    /**
     * 计数归零（与 feed 清空同步）。
     */
    public void reset() {
        delivered.set(0);
        memory.set(0);
        console.set(0);
        mq.set(0);
        mqConsumed.set(0);
    }
}
