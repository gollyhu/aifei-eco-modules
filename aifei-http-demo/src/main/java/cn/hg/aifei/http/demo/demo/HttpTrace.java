/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.demo;

import cn.hg.aifei.http.HttpInterceptor;
import cn.hg.aifei.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出站请求的内存环形记录：拦截器穿链事件（进入 / 收尾）+ 累计计数。
 *
 * <p>只为演示与页面展示服务：容量固定，写满即淘汰最旧事件，不会随请求量增长；进程内、无持久化。
 * 计数口径明确为「一次逻辑请求 = 一次 {@code exec}」：</p>
 *
 * <ul>
 *   <li>{@code requestCount}：逻辑请求数（只统计 {@code attempt == 1} 的首次进入）</li>
 *   <li>{@code attemptCount}：真实尝试次数（含重试产生的尝试）</li>
 *   <li>{@code retryCount}：重试次数（{@code attempt > 1} 的进入次数）</li>
 * </ul>
 *
 * <p>计数只由「主拦截器」贡献（登记时的 {@code primary} 标记），否则同一个记录型拦截器登记 3 个实例会把
 * 计数放大 3 倍。</p>
 */
public final class HttpTrace {

    /**
     * 环形事件容量。
     */
    public static final int CAPACITY = 60;

    /**
     * 穿链事件环形队列；所有访问在类锁内。
     */
    private static final Deque<Map<String, Object>> EVENTS = new ArrayDeque<Map<String, Object>>();

    /**
     * 已登记拦截器名称（登记顺序 = 穿链进入顺序）。
     */
    private static final List<String> INTERCEPTORS = new ArrayList<String>();

    private static int requestCount;

    private static int attemptCount;

    private static int retryCount;

    private static String lastResource;

    private HttpTrace() {
    }

    /**
     * 登记拦截器名称（重复登记同名只保留一次，顺序按首次登记）。
     *
     * @param interceptorName 拦截器名称
     */
    public static synchronized void register(String interceptorName) {
        if (interceptorName != null && !INTERCEPTORS.contains(interceptorName)) {
            INTERCEPTORS.add(interceptorName);
        }
    }

    /**
     * 已登记拦截器名称（登记顺序）。
     *
     * @return 名称列表拷贝
     */
    public static synchronized List<String> interceptors() {
        return new ArrayList<String>(INTERCEPTORS);
    }

    /**
     * 记录一次 {@code before}。
     *
     * @param interceptorName 拦截器名称
     * @param primary 是否为主拦截器（只有主拦截器参与计数）
     * @param attempt 当前尝试的只读视图
     */
    public static synchronized void onBefore(String interceptorName, boolean primary,
                                             HttpInterceptor.HttpAttempt attempt) {
        if (primary) {
            if (attempt.attempt() == 1) {
                requestCount++;
            }
            attemptCount++;
            if (attempt.attempt() > 1) {
                retryCount++;
            }
            lastResource = attempt.resource();
        }
        append(event(interceptorName, "before", attempt));
    }

    /**
     * 记录一次 {@code after}（失败也记录，{@code error} 字段给出异常摘要）。
     *
     * @param interceptorName 拦截器名称
     * @param primary 是否为主拦截器（只有主拦截器参与计数）
     * @param attempt 当前尝试的只读视图
     */
    public static synchronized void onAfter(String interceptorName, boolean primary,
                                            HttpInterceptor.HttpAttempt attempt) {
        append(event(interceptorName, "after", attempt));
    }

    /**
     * 当前事件快照（最旧在前）。
     *
     * @return 事件列表拷贝
     */
    public static synchronized List<Map<String, Object>> snapshot() {
        return new ArrayList<Map<String, Object>>(EVENTS);
    }

    /**
     * 只取事件流中某一阶段（{@code before} / {@code after}）的拦截器名称顺序，供页面直接展示正序 / 逆序。
     *
     * @param phase {@code before} 或 {@code after}
     * @return 拦截器名称顺序
     */
    public static synchronized List<String> order(String phase) {
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> event : EVENTS) {
            if (phase.equals(event.get("phase"))) {
                names.add(String.valueOf(event.get("interceptor")));
            }
        }
        return names;
    }

    /**
     * 最近一次被拦截器读到的资源名（默认推导或显式覆盖的结果）。
     *
     * @return 资源名；尚未发生请求时为 {@code null}
     */
    public static synchronized String lastResource() {
        return lastResource;
    }

    public static synchronized int requestCount() {
        return requestCount;
    }

    public static synchronized int attemptCount() {
        return attemptCount;
    }

    public static synchronized int retryCount() {
        return retryCount;
    }

    public static synchronized int eventCount() {
        return EVENTS.size();
    }

    /**
     * 清空事件与计数（拦截器登记表保留，它属于装配期事实）。
     */
    public static synchronized void reset() {
        EVENTS.clear();
        requestCount = 0;
        attemptCount = 0;
        retryCount = 0;
        lastResource = null;
    }

    private static Map<String, Object> event(String interceptorName, String phase,
                                             HttpInterceptor.HttpAttempt attempt) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("phase", phase);
        event.put("interceptor", interceptorName);
        event.put("attempt", attempt.attempt());
        event.put("resource", attempt.resource());
        event.put("method", attempt.method());
        event.put("url", attempt.url());
        event.put("elapsedMillis", attempt.elapsedMillis());
        HttpResponse response = attempt.response();
        event.put("status", response == null ? null : Integer.valueOf(response.statusCode()));
        Throwable error = attempt.error();
        event.put("error", error == null ? null : error.getClass().getSimpleName() + ": " + error.getMessage());
        return event;
    }

    private static void append(Map<String, Object> event) {
        if (EVENTS.size() >= CAPACITY) {
            EVENTS.pollFirst();
        }
        EVENTS.addLast(event);
    }
}
