/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo.console;

import cn.aifei.log.Log;
import java.util.function.Supplier;

/** demo 自带的极简 Log：System.out 直出，零 slf4j / log4j 绑定。 */
public class ConsoleLog implements Log {

    private final String name;

    public ConsoleLog(String name) {
        this.name = name;
    }

    @Override public String getName() { return name; }
    @Override public boolean isTraceEnabled() { return false; }
    @Override public boolean isDebugEnabled() { return false; }
    @Override public boolean isInfoEnabled() { return true; }
    @Override public boolean isWarnEnabled() { return true; }
    @Override public boolean isErrorEnabled() { return true; }

    @Override public void trace(String msg) { }
    @Override public void trace(String msg, Throwable t) { }
    @Override public void trace(String format, Object arg) { }
    @Override public void trace(String format, Object arg1, Object arg2) { }
    @Override public void trace(String format, Object... args) { }
    @Override public void trace(Supplier<String> supplier) { }
    @Override public void debug(String msg) { }
    @Override public void debug(String msg, Throwable t) { }
    @Override public void debug(String format, Object arg) { }
    @Override public void debug(String format, Object arg1, Object arg2) { }
    @Override public void debug(String format, Object... args) { }
    @Override public void debug(Supplier<String> supplier) { }

    @Override public void info(String msg) { print("INFO", msg, null); }
    @Override public void info(String msg, Throwable t) { print("INFO", msg, t); }
    @Override public void info(String format, Object arg) { print("INFO", format(format, arg), null); }
    @Override public void info(String format, Object arg1, Object arg2) { print("INFO", format(format, arg1, arg2), null); }
    @Override public void info(String format, Object... args) { print("INFO", format(format, args), null); }
    @Override public void info(Supplier<String> supplier) { print("INFO", supplier.get(), null); }

    @Override public void warn(String msg) { print("WARN", msg, null); }
    @Override public void warn(String msg, Throwable t) { print("WARN", msg, t); }
    @Override public void warn(String format, Object arg) { print("WARN", format(format, arg), null); }
    @Override public void warn(String format, Object arg1, Object arg2) { print("WARN", format(format, arg1, arg2), null); }
    @Override public void warn(String format, Object... args) { print("WARN", format(format, args), null); }
    @Override public void warn(Supplier<String> supplier) { print("WARN", supplier.get(), null); }

    @Override public void error(String msg) { print("ERROR", msg, null); }
    @Override public void error(String msg, Throwable t) { print("ERROR", msg, t); }
    @Override public void error(String format, Object arg) { print("ERROR", format(format, arg), null); }
    @Override public void error(String format, Object arg1, Object arg2) { print("ERROR", format(format, arg1, arg2), null); }
    @Override public void error(String format, Object... args) { print("ERROR", format(format, args), null); }
    @Override public void error(Supplier<String> supplier) { print("ERROR", supplier.get(), null); }

    private void print(String level, String msg, Throwable t) {
        System.out.println("[" + level + "] " + name + " : " + msg);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }

    private static String format(String pattern, Object... args) {
        if (pattern == null || args == null) {
            return pattern;
        }
        String result = pattern;
        for (Object arg : args) {
            result = result.replaceFirst("\\{\\}", String.valueOf(arg).replace("$", "\\$"));
        }
        return result;
    }
}
