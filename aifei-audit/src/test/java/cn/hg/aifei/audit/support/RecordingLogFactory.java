/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.support;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;
import cn.aifei.log.LogKit;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 测试用日志工厂：记录级别与消息，供「恰一条告警」一类断言计数，本身不产生任何输出。
 *
 * <p>必要性有两重：① 框架默认 {@code NoLogFactory} 在取日志时直接抛
 * {@code IllegalStateException("LogFactory not configured.")}，故任何触达 {@code Log.get(...)} 的用例
 * （含框架 {@code Scanner} 的类初始化）都必须先装配工厂；② 本模块不允许引入日志绑定，
 * 又必须验证「失败只记一条」，故只能自带记录型替身。</p>
 */
public final class RecordingLogFactory implements LogFactory {

    /**
     * 已记录的日志条目，形如 {@code warn: 消息}。
     */
    private static final List<String> RECORDS = Collections.synchronizedList(new ArrayList<String>());

    private static final Log RECORDING_LOG = newRecordingLog();

    private RecordingLogFactory() {
    }

    /**
     * 换入记录型工厂，并清空已有记录。
     *
     * @return 换入前的工厂，供 {@link #restore(LogFactory)} 还原
     */
    public static LogFactory install() {
        LogFactory previous = LogKit.get().getLogFactory();
        LogKit.get().setLogFactory(new RecordingLogFactory());
        RECORDS.clear();
        return previous;
    }

    /**
     * 还原工厂。
     *
     * @param previous 换入前的工厂
     */
    public static void restore(LogFactory previous) {
        LogKit.get().setLogFactory(previous);
        RECORDS.clear();
    }

    /**
     * 清空记录（同一用例内分段计数时用）。
     */
    public static void clear() {
        RECORDS.clear();
    }

    /**
     * 指定级别的记录条数。
     *
     * @param level 级别名（{@code warn} / {@code error} / {@code info} …）
     * @return 条数
     */
    public static int count(String level) {
        int count = 0;
        synchronized (RECORDS) {
            for (String record : RECORDS) {
                if (record.startsWith(level + ":")) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 记录总条数。
     *
     * @return 条数
     */
    public static int total() {
        return RECORDS.size();
    }

    /**
     * 任一记录是否包含指定片段。
     *
     * @param fragment 片段
     * @return 包含时 {@code true}
     */
    public static boolean contains(String fragment) {
        synchronized (RECORDS) {
            for (String record : RECORDS) {
                if (record.contains(fragment)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Log getLog() {
        return RECORDING_LOG;
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return RECORDING_LOG;
    }

    @Override
    public Log getLog(String name) {
        return RECORDING_LOG;
    }

    /**
     * 记录型空实现：级别询问恒 {@code false}，写日志时把「级别 + 消息」入队后返回。
     *
     * @return 记录型日志
     */
    private static Log newRecordingLog() {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) {
                    return Boolean.FALSE;
                }
                if (returnType == String.class) {
                    return "recording";
                }
                if (args != null && args.length > 0 && isLogLevel(name)) {
                    RECORDS.add(name + ": " + message(args));
                }
                return null;
            }
        };
        return (Log) Proxy.newProxyInstance(Log.class.getClassLoader(), new Class<?>[]{Log.class}, handler);
    }

    private static boolean isLogLevel(String name) {
        return "trace".equals(name) || "debug".equals(name) || "info".equals(name)
                || "warn".equals(name) || "error".equals(name) || "fatal".equals(name);
    }

    /**
     * 把日志参数拼成消息：{@link Supplier} 取值，其余 {@code String.valueOf}，首个参数作为格式串原样保留。
     *
     * @param args 日志参数
     * @return 消息文本
     */
    private static String message(Object[] args) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Supplier) {
                arg = ((Supplier<?>) arg).get();
            }
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(String.valueOf(arg));
        }
        return builder.toString();
    }
}
