/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.console;

import cn.aifei.log.Log;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/** ConsoleLog：demo 自带的极简 Log 实现，输出走 System.out，零日志绑定。 */
public class ConsoleLog implements Log {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");

    final String name;

    public ConsoleLog(String name) {
        this.name = name;
    }

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

    @Override
    public void trace(String msg) { }

    @Override
    public void trace(String msg, Throwable t) { }

    @Override
    public void trace(String format, Object arg) { }

    @Override
    public void trace(String format, Object arg1, Object arg2) { }

    @Override
    public void trace(String format, Object... arguments) { }

    @Override
    public void trace(java.util.function.Supplier<String> messageSupplier) { }

    @Override
    public void debug(String msg) { }

    @Override
    public void debug(String msg, Throwable t) { }

    @Override
    public void debug(String format, Object arg) { }

    @Override
    public void debug(String format, Object arg1, Object arg2) { }

    @Override
    public void debug(String format, Object... arguments) { }

    @Override
    public void debug(java.util.function.Supplier<String> messageSupplier) { }

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

    private void println(String level, String msg, Throwable t) {
        String line = TIME_FORMAT.format(new Date()) + " [" + level + "] " + name + " : " + msg;
        if (t != null) {
            line = line + System.lineSeparator() + stackTraceOf(t);
        }
        System.out.println(line);
    }

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
                i++;
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
