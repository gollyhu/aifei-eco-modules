/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import cn.aifei.log.Log;

/**
 * RecordingLog.
 *
 * <pre>
 * 测试桩：记录底层 Log 收到的打印调用，用于断言 TraceLog 的前缀拼装与透传行为。
 *
 * 两种记录形态：
 *  1: String 变体（含 Throwable / 格式化变体）：调用时刻即求值并记录。
 *  2: Supplier 变体：调用时刻仅暂存 supplier，不求值；须显式调用 flushPendingSupplier() 才求值并记录——
 *     以此验证 TraceLog 的「打印时刻才读链路上下文」懒求值契约。
 * </pre>
 */
public class RecordingLog implements Log {

    /**
     * 日志名称（可由测试改写，用于断言 getName 透传）。
     */
    public String name = "recording-log";

    /**
     * 五个级别的开关状态（可由测试改写，用于断言 isXxxEnabled 透传）。
     */
    public boolean traceEnabled = true;
    public boolean debugEnabled = true;
    public boolean infoEnabled = true;
    public boolean warnEnabled = true;
    public boolean errorEnabled = true;

    /**
     * 已求值打印的级别序列（与 messages 一一对应）。
     */
    public final List<String> levels = new ArrayList<>();

    /**
     * 已求值打印的消息序列（前缀行为由 TraceLog 决定，本桩原样记录收到的消息）。
     */
    public final List<String> messages = new ArrayList<>();

    /**
     * 已求值打印的异常序列（与 messages 一一对应，无异常处为 null）。
     */
    public final List<Throwable> throwables = new ArrayList<>();

    /**
     * 最近一次打印收到的占位参数数组（String 变体原样透传断言用）。
     */
    public Object[] lastArgs;

    /**
     * Supplier 变体暂存：调用时刻收到的消息供给器（未求值）。
     */
    private Supplier<String> pendingSupplier;

    /**
     * Supplier 变体暂存：调用时刻收到的级别。
     */
    private String pendingLevel;

    /**
     * 求值并记录暂存的 Supplier 消息；无暂存时为空操作。
     */
    public void flushPendingSupplier() {
        if (pendingSupplier != null) {
            deliver(pendingLevel, pendingSupplier.get(), null);
            pendingSupplier = null;
            pendingLevel = null;
        }
    }

    /**
     * 记录一次已求值打印。
     *
     * @param level 级别名
     * @param msg   收到的消息文本
     * @param t     收到的异常，可为 null
     * @param args  收到的占位参数
     */
    private void deliver(String level, String msg, Throwable t, Object... args) {
        levels.add(level);
        messages.add(msg);
        throwables.add(t);
        lastArgs = args;
    }

    @Override
    public String getName() {
        return name;
    }

    // ---------------------------------------------------------------------------------------------------------
    // TRACE

    @Override
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    @Override
    public void trace(String msg) {
        deliver("TRACE", msg, null);
    }

    @Override
    public void trace(String msg, Throwable t) {
        deliver("TRACE", msg, t);
    }

    @Override
    public void trace(String format, Object arg) {
        deliver("TRACE", format, null, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        deliver("TRACE", format, null, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
        deliver("TRACE", format, null, arguments);
    }

    @Override
    public void trace(Supplier<String> messageSupplier) {
        pendingSupplier = messageSupplier;
        pendingLevel = "TRACE";
    }

    // ---------------------------------------------------------------------------------------------------------
    // DEBUG

    @Override
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    @Override
    public void debug(String msg) {
        deliver("DEBUG", msg, null);
    }

    @Override
    public void debug(String msg, Throwable t) {
        deliver("DEBUG", msg, t);
    }

    @Override
    public void debug(String format, Object arg) {
        deliver("DEBUG", format, null, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        deliver("DEBUG", format, null, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        deliver("DEBUG", format, null, arguments);
    }

    @Override
    public void debug(Supplier<String> messageSupplier) {
        pendingSupplier = messageSupplier;
        pendingLevel = "DEBUG";
    }

    // ---------------------------------------------------------------------------------------------------------
    // INFO

    @Override
    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    @Override
    public void info(String msg) {
        deliver("INFO", msg, null);
    }

    @Override
    public void info(String msg, Throwable t) {
        deliver("INFO", msg, t);
    }

    @Override
    public void info(String format, Object arg) {
        deliver("INFO", format, null, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        deliver("INFO", format, null, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        deliver("INFO", format, null, arguments);
    }

    @Override
    public void info(Supplier<String> messageSupplier) {
        pendingSupplier = messageSupplier;
        pendingLevel = "INFO";
    }

    // ---------------------------------------------------------------------------------------------------------
    // WARN

    @Override
    public boolean isWarnEnabled() {
        return warnEnabled;
    }

    @Override
    public void warn(String msg) {
        deliver("WARN", msg, null);
    }

    @Override
    public void warn(String msg, Throwable t) {
        deliver("WARN", msg, t);
    }

    @Override
    public void warn(String format, Object arg) {
        deliver("WARN", format, null, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        deliver("WARN", format, null, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        deliver("WARN", format, null, arguments);
    }

    @Override
    public void warn(Supplier<String> messageSupplier) {
        pendingSupplier = messageSupplier;
        pendingLevel = "WARN";
    }

    // ---------------------------------------------------------------------------------------------------------
    // ERROR

    @Override
    public boolean isErrorEnabled() {
        return errorEnabled;
    }

    @Override
    public void error(String msg) {
        deliver("ERROR", msg, null);
    }

    @Override
    public void error(String msg, Throwable t) {
        deliver("ERROR", msg, t);
    }

    @Override
    public void error(String format, Object arg) {
        deliver("ERROR", format, null, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        deliver("ERROR", format, null, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        deliver("ERROR", format, null, arguments);
    }

    @Override
    public void error(Supplier<String> messageSupplier) {
        pendingSupplier = messageSupplier;
        pendingLevel = "ERROR";
    }
}
