/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import java.util.function.Supplier;
import cn.aifei.log.Log;

/**
 * Log 装饰器：在打印时刻为每条消息拼装 "[traceId] " 前缀，使日志自动携带链路标识。
 *
 * <p>TraceId 缺失时不加前缀、不报错、不打警告，消息原样透出（高频路径零开销）。</p>
 *
 * <p>前缀在调用线程上读取 TraceContext.getTraceId()：同一 Log 实例跨线程使用时，
 * 各自读到各自线程的 TraceId（本类无状态、只有 final delegate 字段，线程安全）。</p>
 *
 * <p>Supplier 参数变体采用懒求值：前缀在打印时刻读取链路上下文，
 * 保证异步日志场景取到的是打印时刻的链路上下文。</p>
 *
 * <p>通常不直接 new，而是经 TraceLogFactory 装配：</p>
 * <pre>{@code settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()));}</pre>
 */
public class TraceLog implements Log {

    private final Log delegate;

    /**
     * 构造装饰器。
     *
     * @param delegate 被装饰的底层 Log 实现，不得为 null
     * @throws IllegalArgumentException delegate 为 null 时抛出
     */
    public TraceLog(Log delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate can not be null.");
        }
        this.delegate = delegate;
    }

    /**
     * 在打印时刻计算 "[traceId] " 前缀（本模块前缀拼装的唯一定义点）。
     *
     * @return TraceId 非 null 时返回 "[traceId] "，缺失时返回空串
     */
    private String prefix() {
        String traceId = TraceContext.getTraceId();
        return traceId == null ? "" : "[" + traceId + "] ";
    }

    /**
     * 获取底层日志的名称（纯透传，不拼前缀）。
     *
     * @return delegate 的日志名称
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    // ---------------------------------------------------------------------------------------------------------
    // TRACE

    /**
     * 是否启用 TRACE 级别（纯透传 delegate）。
     *
     * @return delegate 的 TRACE 开关状态
     */
    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    /**
     * 打印 TRACE 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null（前缀仍拼装，拼接结果交由 delegate 处理）
     */
    @Override
    public void trace(String msg) {
        delegate.trace(prefix() + msg);
    }

    /**
     * 打印带异常的 TRACE 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     * @param t   随消息打印的异常，可为 null
     */
    @Override
    public void trace(String msg, Throwable t) {
        delegate.trace(prefix() + msg, t);
    }

    /**
     * 打印单占位符格式化的 TRACE 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg    占位符参数
     */
    @Override
    public void trace(String format, Object arg) {
        delegate.trace(prefix() + format, arg);
    }

    /**
     * 打印双占位符格式化的 TRACE 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg1   第一个占位符参数
     * @param arg2   第二个占位符参数
     */
    @Override
    public void trace(String format, Object arg1, Object arg2) {
        delegate.trace(prefix() + format, arg1, arg2);
    }

    /**
     * 打印多占位符格式化的 TRACE 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format    格式串（含 {} 占位符）
     * @param arguments 占位符参数数组
     */
    @Override
    public void trace(String format, Object... arguments) {
        delegate.trace(prefix() + format, arguments);
    }

    /**
     * 懒求值打印 TRACE 级别消息：前缀在打印时刻读取链路上下文，保证异步日志取到打印线程的 TraceId。
     *
     * @param messageSupplier 消息惰性供给器，打印时刻才调用
     */
    @Override
    public void trace(Supplier<String> messageSupplier) {
        delegate.trace(() -> prefix() + messageSupplier.get());
    }

    // ---------------------------------------------------------------------------------------------------------
    // DEBUG

    /**
     * 是否启用 DEBUG 级别（纯透传 delegate）。
     *
     * @return delegate 的 DEBUG 开关状态
     */
    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    /**
     * 打印 DEBUG 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     */
    @Override
    public void debug(String msg) {
        delegate.debug(prefix() + msg);
    }

    /**
     * 打印带异常的 DEBUG 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     * @param t   随消息打印的异常，可为 null
     */
    @Override
    public void debug(String msg, Throwable t) {
        delegate.debug(prefix() + msg, t);
    }

    /**
     * 打印单占位符格式化的 DEBUG 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg    占位符参数
     */
    @Override
    public void debug(String format, Object arg) {
        delegate.debug(prefix() + format, arg);
    }

    /**
     * 打印双占位符格式化的 DEBUG 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg1   第一个占位符参数
     * @param arg2   第二个占位符参数
     */
    @Override
    public void debug(String format, Object arg1, Object arg2) {
        delegate.debug(prefix() + format, arg1, arg2);
    }

    /**
     * 打印多占位符格式化的 DEBUG 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format    格式串（含 {} 占位符）
     * @param arguments 占位符参数数组
     */
    @Override
    public void debug(String format, Object... arguments) {
        delegate.debug(prefix() + format, arguments);
    }

    /**
     * 懒求值打印 DEBUG 级别消息：前缀在打印时刻读取链路上下文，保证异步日志取到打印线程的 TraceId。
     *
     * @param messageSupplier 消息惰性供给器，打印时刻才调用
     */
    @Override
    public void debug(Supplier<String> messageSupplier) {
        delegate.debug(() -> prefix() + messageSupplier.get());
    }

    // ---------------------------------------------------------------------------------------------------------
    // INFO

    /**
     * 是否启用 INFO 级别（纯透传 delegate）。
     *
     * @return delegate 的 INFO 开关状态
     */
    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    /**
     * 打印 INFO 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     */
    @Override
    public void info(String msg) {
        delegate.info(prefix() + msg);
    }

    /**
     * 打印带异常的 INFO 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     * @param t   随消息打印的异常，可为 null
     */
    @Override
    public void info(String msg, Throwable t) {
        delegate.info(prefix() + msg, t);
    }

    /**
     * 打印单占位符格式化的 INFO 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg    占位符参数
     */
    @Override
    public void info(String format, Object arg) {
        delegate.info(prefix() + format, arg);
    }

    /**
     * 打印双占位符格式化的 INFO 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg1   第一个占位符参数
     * @param arg2   第二个占位符参数
     */
    @Override
    public void info(String format, Object arg1, Object arg2) {
        delegate.info(prefix() + format, arg1, arg2);
    }

    /**
     * 打印多占位符格式化的 INFO 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format    格式串（含 {} 占位符）
     * @param arguments 占位符参数数组
     */
    @Override
    public void info(String format, Object... arguments) {
        delegate.info(prefix() + format, arguments);
    }

    /**
     * 懒求值打印 INFO 级别消息：前缀在打印时刻读取链路上下文，保证异步日志取到打印线程的 TraceId。
     *
     * @param messageSupplier 消息惰性供给器，打印时刻才调用
     */
    @Override
    public void info(Supplier<String> messageSupplier) {
        delegate.info(() -> prefix() + messageSupplier.get());
    }

    // ---------------------------------------------------------------------------------------------------------
    // WARN

    /**
     * 是否启用 WARN 级别（纯透传 delegate）。
     *
     * @return delegate 的 WARN 开关状态
     */
    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    /**
     * 打印 WARN 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     */
    @Override
    public void warn(String msg) {
        delegate.warn(prefix() + msg);
    }

    /**
     * 打印带异常的 WARN 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     * @param t   随消息打印的异常，可为 null
     */
    @Override
    public void warn(String msg, Throwable t) {
        delegate.warn(prefix() + msg, t);
    }

    /**
     * 打印单占位符格式化的 WARN 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg    占位符参数
     */
    @Override
    public void warn(String format, Object arg) {
        delegate.warn(prefix() + format, arg);
    }

    /**
     * 打印双占位符格式化的 WARN 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg1   第一个占位符参数
     * @param arg2   第二个占位符参数
     */
    @Override
    public void warn(String format, Object arg1, Object arg2) {
        delegate.warn(prefix() + format, arg1, arg2);
    }

    /**
     * 打印多占位符格式化的 WARN 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format    格式串（含 {} 占位符）
     * @param arguments 占位符参数数组
     */
    @Override
    public void warn(String format, Object... arguments) {
        delegate.warn(prefix() + format, arguments);
    }

    /**
     * 懒求值打印 WARN 级别消息：前缀在打印时刻读取链路上下文，保证异步日志取到打印线程的 TraceId。
     *
     * @param messageSupplier 消息惰性供给器，打印时刻才调用
     */
    @Override
    public void warn(Supplier<String> messageSupplier) {
        delegate.warn(() -> prefix() + messageSupplier.get());
    }

    // ---------------------------------------------------------------------------------------------------------
    // ERROR

    /**
     * 是否启用 ERROR 级别（纯透传 delegate）。
     *
     * @return delegate 的 ERROR 开关状态
     */
    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    /**
     * 打印 ERROR 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     */
    @Override
    public void error(String msg) {
        delegate.error(prefix() + msg);
    }

    /**
     * 打印带异常的 ERROR 级别消息，自动拼装 TraceId 前缀。
     *
     * @param msg 消息文本，可为 null
     * @param t   随消息打印的异常，可为 null
     */
    @Override
    public void error(String msg, Throwable t) {
        delegate.error(prefix() + msg, t);
    }

    /**
     * 打印单占位符格式化的 ERROR 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg    占位符参数
     */
    @Override
    public void error(String format, Object arg) {
        delegate.error(prefix() + format, arg);
    }

    /**
     * 打印双占位符格式化的 ERROR 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format 格式串（含 {} 占位符）
     * @param arg1   第一个占位符参数
     * @param arg2   第二个占位符参数
     */
    @Override
    public void error(String format, Object arg1, Object arg2) {
        delegate.error(prefix() + format, arg1, arg2);
    }

    /**
     * 打印多占位符格式化的 ERROR 级别消息，前缀只拼在 format 串上，占位参数原样传递。
     *
     * @param format    格式串（含 {} 占位符）
     * @param arguments 占位符参数数组
     */
    @Override
    public void error(String format, Object... arguments) {
        delegate.error(prefix() + format, arguments);
    }

    /**
     * 懒求值打印 ERROR 级别消息：前缀在打印时刻读取链路上下文，保证异步日志取到打印线程的 TraceId。
     *
     * @param messageSupplier 消息惰性供给器，打印时刻才调用
     */
    @Override
    public void error(Supplier<String> messageSupplier) {
        delegate.error(() -> prefix() + messageSupplier.get());
    }
}
