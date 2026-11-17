/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.excel.demo.console;

import cn.aifei.log.Log;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ConsoleLog：demo 自带的极简 Log 实现，全部输出走 {@code System.out}，
 * <b>不引入任何 slf4j / log4j 绑定</b>（维持 demo 零第三方纪律）。
 *
 * <p>无任何装饰链（与 {@code aifei-trace-demo} 的差异点）：msg 原样落地，控制台每行形如
 * {@code [INFO] cn.hg.aifei.excel.demo.service.ExcelDemoService : ...}。</p>
 *
 * <p>额外能力：内置一个小容量环形缓冲（demo 内部演示用），保存最近若干行已打印日志，
 * 供 Service 端点回传给页面展示「控制台输出样本」。trace / debug 级别默认关闭，避免刷屏。</p>
 */
public class ConsoleLog implements Log {

    /**
     * 环形缓冲容量（demo 演示用，非生产语义）。
     */
    static final int HISTORY_CAPACITY = 100;

    /**
     * 最近打印过的日志行（先进先出，越界淘汰最旧）；ConsoleLog 无状态，缓冲为进程级共享。
     */
    static final ArrayDeque<String> HISTORY = new ArrayDeque<String>(HISTORY_CAPACITY);

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    final String name;

    /**
     * 构造方法
     *
     * @param name 日志名，通常为类名
     */
    public ConsoleLog(String name) {
        this.name = name;
    }

    /**
     * 取最近打印的日志行快照（旧 -> 新），供页面展示控制台输出样本。
     *
     * @return 日志行列表副本，可能为空，不会为 null
     */
    public static List<String> recentLines() {
        synchronized (HISTORY) {
            return new ArrayList<String>(HISTORY);
        }
    }

    /**
     * 清空缓冲（页面「清空控制台样本」用）。
     */
    public static void clearHistory() {
        synchronized (HISTORY) {
            HISTORY.clear();
        }
    }

    // ----------------------------------------------------------------------------------
    // 五级开关：trace / debug 关闭，info / warn / error 开启
    // ----------------------------------------------------------------------------------

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return name;
    }

    // ----------------------------------------------------------------------------------
    // 各级别打印方法：统一转调 println(level, msg, t)，msg 已由 TraceLog 拼好前缀
    // ----------------------------------------------------------------------------------

    @Override
    public void trace(String msg) {
        // trace 级别关闭，静默丢弃
    }

    @Override
    public void trace(String msg, Throwable t) {
        // trace 级别关闭，静默丢弃
    }

    @Override
    public void trace(String format, Object arg) {
        // trace 级别关闭，静默丢弃
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        // trace 级别关闭，静默丢弃
    }

    @Override
    public void trace(String format, Object... arguments) {
        // trace 级别关闭，静默丢弃
    }

    @Override
    public void trace(java.util.function.Supplier<String> messageSupplier) {
        // trace 级别关闭，静默丢弃（Supplier 不求值，零开销）
    }

    @Override
    public void debug(String msg) {
        // debug 级别关闭，静默丢弃
    }

    @Override
    public void debug(String msg, Throwable t) {
        // debug 级别关闭，静默丢弃
    }

    @Override
    public void debug(String format, Object arg) {
        // debug 级别关闭，静默丢弃
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        // debug 级别关闭，静默丢弃
    }

    @Override
    public void debug(String format, Object... arguments) {
        // debug 级别关闭，静默丢弃
    }

    @Override
    public void debug(java.util.function.Supplier<String> messageSupplier) {
        // debug 级别关闭，静默丢弃（Supplier 不求值，零开销）
    }

    @Override
    public void info(String msg) {
        println("INFO", msg, null);
    }

    @Override
    public void info(String msg, Throwable t) {
        println("INFO", msg, t);
    }

    @Override
    public void info(String format, Object arg) {
        println("INFO", format(format, arg), null);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        println("INFO", format(format, arg1, arg2), null);
    }

    @Override
    public void info(String format, Object... arguments) {
        println("INFO", format(format, arguments), null);
    }

    @Override
    public void info(java.util.function.Supplier<String> messageSupplier) {
        println("INFO", messageSupplier.get(), null);
    }

    @Override
    public void warn(String msg) {
        println("WARN", msg, null);
    }

    @Override
    public void warn(String msg, Throwable t) {
        println("WARN", msg, t);
    }

    @Override
    public void warn(String format, Object arg) {
        println("WARN", format(format, arg), null);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        println("WARN", format(format, arg1, arg2), null);
    }

    @Override
    public void warn(String format, Object... arguments) {
        println("WARN", format(format, arguments), null);
    }

    @Override
    public void warn(java.util.function.Supplier<String> messageSupplier) {
        println("WARN", messageSupplier.get(), null);
    }

    @Override
    public void error(String msg) {
        println("ERROR", msg, null);
    }

    @Override
    public void error(String msg, Throwable t) {
        println("ERROR", msg, t);
    }

    @Override
    public void error(String format, Object arg) {
        println("ERROR", format(format, arg), null);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        println("ERROR", format(format, arg1, arg2), null);
    }

    @Override
    public void error(String format, Object... arguments) {
        println("ERROR", format(format, arguments), null);
    }

    @Override
    public void error(java.util.function.Supplier<String> messageSupplier) {
        println("ERROR", messageSupplier.get(), null);
    }

    // ----------------------------------------------------------------------------------
    // 内部工具
    // ----------------------------------------------------------------------------------

    /**
     * 唯一输出点：控制台打印一行 + 写入环形缓冲。
     */
    private void println(String level, String msg, Throwable t) {
        String line = TIME_FORMAT.format(new Date()) + " [" + level + "] " + name + " : " + msg;
        if (t != null) {
            line = line + System.lineSeparator() + stackTraceOf(t);
        }
        System.out.println(line);
        synchronized (HISTORY) {
            if (HISTORY.size() >= HISTORY_CAPACITY) {
                HISTORY.pollFirst();
            }
            HISTORY.addLast(line);
        }
    }

    /**
     * 使用 MessageFormat 语义做 {} 占位替换（与 aifei-log 各实现的 format 用法保持一致）。
     */
    private static String format(String format, Object... arguments) {
        if (format == null || arguments == null || arguments.length == 0) {
            return format;
        }
        StringBuilder ret = new StringBuilder(format.length() + 32);
        int argIndex = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '{' && argIndex < arguments.length
                    && i + 1 < format.length() && format.charAt(i + 1) == '}') {
                ret.append(arguments[argIndex++]);
                i++;    // 跳过 '}'
            } else {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
