/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.feed;

import cn.hg.aifei.audit.AuditEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 页面读模型：保存最近 200 条事件的视图快照（这是 demo 自己的展示数据，不是模块能力）。
 */
public final class EventFeed {

    private static final int CAPACITY = 200;

    private static final EventFeed INSTANCE = new EventFeed();

    private final List<Map<String, Object>> events = Collections.synchronizedList(new ArrayList<Map<String, Object>>());

    private final AtomicLong sequence = new AtomicLong();

    private EventFeed() {
    }

    /**
     * @return 单例
     */
    public static EventFeed get() {
        return INSTANCE;
    }

    /**
     * 追加一条事件视图。
     *
     * @param event 已交付落点的事件
     */
    public void append(AuditEvent event) {
        long seq = sequence.incrementAndGet();
        events.add(view(seq, event));
        if (events.size() > CAPACITY) {
            events.remove(0);
        }
    }

    /**
     * @return 当前最大序号
     */
    public long seq() {
        return sequence.get();
    }

    /**
     * @param since 上次读取到的序号
     * @return 序号大于 {@code since} 的事件视图
     */
    public List<Map<String, Object>> since(long since) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        synchronized (events) {
            for (int i = 0; i < events.size(); i++) {
                Map<String, Object> item = events.get(i);
                Number seq = (Number) item.get("seq");
                if (seq.longValue() > since) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /**
     * @return 最近一条事件视图；空态为 {@code null}
     */
    public Map<String, Object> last() {
        synchronized (events) {
            return events.isEmpty() ? null : events.get(events.size() - 1);
        }
    }

    /**
     * @return 最近一条失败事件视图；无失败事件为 {@code null}
     */
    public Map<String, Object> lastFailure() {
        synchronized (events) {
            for (int i = events.size() - 1; i >= 0; i--) {
                Map<String, Object> item = events.get(i);
                if ("FAILURE".equals(item.get("result"))) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * 清空展示数据并把序号归零。
     */
    public void reset() {
        events.clear();
        sequence.set(0L);
    }

    private static Map<String, Object> view(long seq, AuditEvent event) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("seq", seq);
        item.put("eventId", event.getEventId());
        item.put("occurredAt", event.getOccurredAt());
        item.put("action", event.getAction());
        item.put("module", event.getModule());
        item.put("desc", event.getDesc());
        item.put("actorId", event.getActorId());
        item.put("actorName", event.getActorName());
        item.put("target", event.getTarget());
        item.put("argsSummary", event.getArgsSummary());
        item.put("result", event.getResult() == null ? null : event.getResult().name());
        item.put("errorCode", event.getErrorCode());
        item.put("errorMessage", event.getErrorMessage());
        item.put("elapsedMillis", event.getElapsedMillis());
        item.put("traceId", event.getTraceId());
        item.put("client", event.getClient());
        return item;
    }
}
