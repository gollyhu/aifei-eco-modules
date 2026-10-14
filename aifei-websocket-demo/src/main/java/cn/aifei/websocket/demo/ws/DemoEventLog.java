/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.websocket.demo.ws;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * 演示用的进程内有界事件日志：客户端监听器与服务端监听器把收到的消息、状态变化与重连事件追加进来，
 * 由 {@code /ws/demo/state} 与 {@code /ws/demo/log} 端点读出去，便于现场肉眼验证行为。
 *
 * <p>日志有固定上限（{@link #MAX_ENTRIES}），超出后丢弃最旧一条，避免演示长时间运行时无界增长。所有方法
 * 均可从任意线程调用（监听器回调线程与 HTTP 请求线程会并发访问）。</p>
 */
public final class DemoEventLog {

    /**
     * 日志条目上限；超出后丢弃最旧一条。
     */
    public static final int MAX_ENTRIES = 200;

    /**
     * 条目时间戳格式。
     */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 保护 {@link #ENTRIES} 的锁。
     */
    private static final Object LOCK = new Object();

    /**
     * 事件条目，按发生顺序保存；受 {@link #LOCK} 保护。
     */
    private static final Deque<String> ENTRIES = new ArrayDeque<String>();

    private DemoEventLog() {
    }

    /**
     * 追加一条事件。
     *
     * @param type 事件类型，例如 {@code CLIENT_SEND} / {@code SERVER_RECV}
     * @param detail 事件细节；为 {@code null} 时按空串记录
     * @throws IllegalArgumentException {@code type} 为 {@code null} 或空白
     */
    public static void record(String type, String detail) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("type can not be blank");
        }
        String line = LocalDateTime.now().format(TIMESTAMP_FORMAT)
                + " [" + type + "] " + (detail != null ? detail : "");
        synchronized (LOCK) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.removeFirst();
            }
            ENTRIES.addLast(line);
        }
    }

    /**
     * 返回当前日志快照。
     *
     * @return 日志条目副本；最新的在前，不为 {@code null}
     */
    public static List<String> snapshot() {
        synchronized (LOCK) {
            List<String> copy = new ArrayList<String>(ENTRIES.size());
            Iterator<String> iterator = ENTRIES.descendingIterator();
            while (iterator.hasNext()) {
                copy.add(iterator.next());
            }
            return copy;
        }
    }

    /**
     * 返回当前日志条目数。
     *
     * @return 条目数，最大为 {@link #MAX_ENTRIES}
     */
    public static int size() {
        synchronized (LOCK) {
            return ENTRIES.size();
        }
    }

    /**
     * 清空日志，便于演示前重置现场。
     */
    public static void clear() {
        synchronized (LOCK) {
            ENTRIES.clear();
        }
    }
}
