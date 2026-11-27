/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.support;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 日志录制替身：实现框架可插拔的 {@link LogFactory}，把每个日志调用按「级别 + 首参消息」录制。
 *
 * <p>用于独立断言日志分级口径（未命中=DEBUG、故障/降级/跳过=WARN）：
 * 产品源码经 {@code Log.get(Xxx.class)} 惰性取 logger，{@code Log.get} 每次实时读 {@code LogKit.logFactory}，
 * 故注入本工厂后所有日志调用均被录制。使用方在 {@code @After} 必须恢复原工厂，避免测试间污染。</p>
 *
 * <p>用 JDK 动态代理实现 {@link Log}，避免逐个实现 30+ 重载；仅拦截级别方法与 getName/isXxxEnabled。</p>
 */
public final class RecordingLogFactory implements LogFactory {

    /** 一条录制事件。 */
    public static final class Event {
        public final String level;
        public final String message;

        Event(String level, String message) {
            this.level = level;
            this.message = message;
        }

        @Override
        public String toString() {
            return level + " | " + message;
        }
    }

    private final List<Event> events = new CopyOnWriteArrayList<Event>();

    @Override
    public Log getLog() {
        return proxy("root");
    }

    @Override
    public Log getLog(Class<?> type) {
        return proxy(type == null ? "null" : type.getName());
    }

    @Override
    public Log getLog(String name) {
        return proxy(name);
    }

    /** @return 全部录制事件（快照） */
    public List<Event> events() {
        return new ArrayList<Event>(events);
    }

    /** @return 指定级别的录制事件 */
    public List<Event> ofLevel(String level) {
        List<Event> out = new ArrayList<Event>();
        for (Event e : events) {
            if (e.level.equals(level)) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * 是否存在某级别、且消息同时包含全部给定片段的事件。
     */
    public boolean has(String level, String... fragments) {
        for (Event e : events) {
            if (!e.level.equals(level)) {
                continue;
            }
            boolean all = true;
            for (String f : fragments) {
                if (!e.message.contains(f)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** 清空录制。 */
    public void clear() {
        events.clear();
    }

    private Log proxy(final String name) {
        return (Log) Proxy.newProxyInstance(Log.class.getClassLoader(), new Class<?>[]{Log.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String mn = method.getName();
                        if ("getName".equals(mn)) {
                            return name;
                        }
                        if (mn.startsWith("is") && mn.endsWith("Enabled")) {
                            return Boolean.TRUE;
                        }
                        if (isLevel(mn)) {
                            String msg = "";
                            if (args != null && args.length > 0) {
                                Object first = args[0];
                                msg = first instanceof Supplier
                                        ? String.valueOf(((Supplier<?>) first).get())
                                        : String.valueOf(first);
                            }
                            events.add(new Event(mn.toUpperCase(Locale.ROOT), msg));
                        }
                        return null;
                    }
                });
    }

    private static boolean isLevel(String methodName) {
        return "trace".equals(methodName) || "debug".equals(methodName)
                || "info".equals(methodName) || "warn".equals(methodName)
                || "error".equals(methodName);
    }
}
