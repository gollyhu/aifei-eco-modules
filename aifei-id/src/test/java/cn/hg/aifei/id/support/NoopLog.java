/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.aifei.log.Log;
import java.util.function.Supplier;

/**
 * 单测用空日志：所有级别关闭、所有输出丢弃，只如实返回名称。
 *
 * <p>内核自带的默认日志工厂在取用即抛异常（提示未配置日志实现），单测若走到模块的记录路径会被这个异常
 * 打断；本实现让记录调用真实发生且零副作用，用例因此只断言业务行为。</p>
 */
public final class NoopLog implements Log {

    /**
     * 日志名称。
     */
    private final String name;

    /**
     * 以名称构造空日志。
     *
     * @param name 日志名称
     */
    public NoopLog(String name) {
        this.name = name;
    }

    /**
     * 返回日志名称。
     *
     * @return 日志名称
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * TRACE 级别恒关闭。
     *
     * @return 恒为 {@code false}
     */
    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    /**
     * 丢弃 TRACE 记录。
     *
     * @param msg 消息
     * @param t 异常
     */
    @Override
    public void trace(String msg, Throwable t) {
    }

    /**
     * 丢弃 TRACE 记录。
     *
     * @param msg 消息
     */
    @Override
    public void trace(String msg) {
    }

    /**
     * 丢弃 TRACE 记录。
     *
     * @param format 消息模板
     * @param arg 参数
     */
    @Override
    public void trace(String format, Object arg) {
    }

    /**
     * 丢弃 TRACE 记录。
     *
     * @param format 消息模板
     * @param arg1 参数一
     * @param arg2 参数二
     */
    @Override
    public void trace(String format, Object arg1, Object arg2) {
    }

    /**
     * 丢弃 TRACE 记录。
     *
     * @param format 消息模板
     * @param arguments 参数
     */
    @Override
    public void trace(String format, Object... arguments) {
    }

    /**
     * 丢弃 TRACE 记录（不求值）。
     *
     * @param messageSupplier 消息供给器
     */
    @Override
    public void trace(Supplier<String> messageSupplier) {
    }

    /**
     * DEBUG 级别恒关闭。
     *
     * @return 恒为 {@code false}
     */
    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    /**
     * 丢弃 DEBUG 记录。
     *
     * @param msg 消息
     * @param t 异常
     */
    @Override
    public void debug(String msg, Throwable t) {
    }

    /**
     * 丢弃 DEBUG 记录。
     *
     * @param msg 消息
     */
    @Override
    public void debug(String msg) {
    }

    /**
     * 丢弃 DEBUG 记录。
     *
     * @param format 消息模板
     * @param arg 参数
     */
    @Override
    public void debug(String format, Object arg) {
    }

    /**
     * 丢弃 DEBUG 记录。
     *
     * @param format 消息模板
     * @param arg1 参数一
     * @param arg2 参数二
     */
    @Override
    public void debug(String format, Object arg1, Object arg2) {
    }

    /**
     * 丢弃 DEBUG 记录。
     *
     * @param format 消息模板
     * @param arguments 参数
     */
    @Override
    public void debug(String format, Object... arguments) {
    }

    /**
     * 丢弃 DEBUG 记录（不求值）。
     *
     * @param messageSupplier 消息供给器
     */
    @Override
    public void debug(Supplier<String> messageSupplier) {
    }

    /**
     * INFO 级别恒关闭。
     *
     * @return 恒为 {@code false}
     */
    @Override
    public boolean isInfoEnabled() {
        return false;
    }

    /**
     * 丢弃 INFO 记录。
     *
     * @param msg 消息
     * @param t 异常
     */
    @Override
    public void info(String msg, Throwable t) {
    }

    /**
     * 丢弃 INFO 记录。
     *
     * @param msg 消息
     */
    @Override
    public void info(String msg) {
    }

    /**
     * 丢弃 INFO 记录。
     *
     * @param format 消息模板
     * @param arg 参数
     */
    @Override
    public void info(String format, Object arg) {
    }

    /**
     * 丢弃 INFO 记录。
     *
     * @param format 消息模板
     * @param arg1 参数一
     * @param arg2 参数二
     */
    @Override
    public void info(String format, Object arg1, Object arg2) {
    }

    /**
     * 丢弃 INFO 记录。
     *
     * @param format 消息模板
     * @param arguments 参数
     */
    @Override
    public void info(String format, Object... arguments) {
    }

    /**
     * 丢弃 INFO 记录（不求值）。
     *
     * @param messageSupplier 消息供给器
     */
    @Override
    public void info(Supplier<String> messageSupplier) {
    }

    /**
     * WARN 级别恒关闭。
     *
     * @return 恒为 {@code false}
     */
    @Override
    public boolean isWarnEnabled() {
        return false;
    }

    /**
     * 丢弃 WARN 记录。
     *
     * @param msg 消息
     * @param t 异常
     */
    @Override
    public void warn(String msg, Throwable t) {
    }

    /**
     * 丢弃 WARN 记录。
     *
     * @param msg 消息
     */
    @Override
    public void warn(String msg) {
    }

    /**
     * 丢弃 WARN 记录。
     *
     * @param format 消息模板
     * @param arg 参数
     */
    @Override
    public void warn(String format, Object arg) {
    }

    /**
     * 丢弃 WARN 记录。
     *
     * @param format 消息模板
     * @param arg1 参数一
     * @param arg2 参数二
     */
    @Override
    public void warn(String format, Object arg1, Object arg2) {
    }

    /**
     * 丢弃 WARN 记录。
     *
     * @param format 消息模板
     * @param arguments 参数
     */
    @Override
    public void warn(String format, Object... arguments) {
    }

    /**
     * 丢弃 WARN 记录（不求值）。
     *
     * @param messageSupplier 消息供给器
     */
    @Override
    public void warn(Supplier<String> messageSupplier) {
    }

    /**
     * ERROR 级别恒关闭。
     *
     * @return 恒为 {@code false}
     */
    @Override
    public boolean isErrorEnabled() {
        return false;
    }

    /**
     * 丢弃 ERROR 记录。
     *
     * @param msg 消息
     * @param t 异常
     */
    @Override
    public void error(String msg, Throwable t) {
    }

    /**
     * 丢弃 ERROR 记录。
     *
     * @param msg 消息
     */
    @Override
    public void error(String msg) {
    }

    /**
     * 丢弃 ERROR 记录。
     *
     * @param format 消息模板
     * @param arg 参数
     */
    @Override
    public void error(String format, Object arg) {
    }

    /**
     * 丢弃 ERROR 记录。
     *
     * @param format 消息模板
     * @param arg1 参数一
     * @param arg2 参数二
     */
    @Override
    public void error(String format, Object arg1, Object arg2) {
    }

    /**
     * 丢弃 ERROR 记录。
     *
     * @param format 消息模板
     * @param arguments 参数
     */
    @Override
    public void error(String format, Object... arguments) {
    }

    /**
     * 丢弃 ERROR 记录（不求值）。
     *
     * @param messageSupplier 消息供给器
     */
    @Override
    public void error(Supplier<String> messageSupplier) {
    }
}
