/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;

/**
 * TraceLogFactory.
 *
 * <pre>
 * LogFactory 装饰器：将任意 LogFactory 产出的 Log 包装为携带 "[traceId] " 前缀的 TraceLog。
 *
 * 用法（与 aifei-log 各工厂天然兼容，适配器由消费方经 aifei-all 自带，本模块不造日志）：
 *   settings.setLogFactory(new TraceLogFactory(new Slf4jLogFactory()));
 *   settings.setLogFactory(new TraceLogFactory(new Log4jLogFactory()));
 *
 * delegate 必须显式给定，避免自包装造成的无限递归：
 * new TraceLogFactory(new TraceLogFactory(...)) 或包装自身工厂属使用方错误，模块不检测不防护。
 * </pre>
 */
public class TraceLogFactory implements LogFactory {

    private final LogFactory delegate;

    /**
     * 构造装饰器。
     *
     * @param delegate 被装饰的底层 LogFactory 实现，不得为 null（必须显式给定，防自包装无限递归）
     * @throws IllegalArgumentException delegate 为 null 时抛出
     */
    public TraceLogFactory(LogFactory delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate can not be null.");
        }
        this.delegate = delegate;
    }

    /**
     * 取无参日志并包装为 TraceLog（每次取用新建装饰器，TraceLog 无状态成本为零）。
     *
     * @return 包装了 delegate 产出的 Log 的 TraceLog
     */
    @Override
    public Log getLog() {
        return new TraceLog(delegate.getLog());
    }

    /**
     * 取指定类型的日志并包装为 TraceLog。
     *
     * @param clazz 日志所属类型
     * @return 包装了 delegate 产出的 Log 的 TraceLog
     */
    @Override
    public Log getLog(Class<?> clazz) {
        return new TraceLog(delegate.getLog(clazz));
    }

    /**
     * 取指定名称的日志并包装为 TraceLog。
     *
     * @param name 日志名称
     * @return 包装了 delegate 产出的 Log 的 TraceLog
     */
    @Override
    public Log getLog(String name) {
        return new TraceLog(delegate.getLog(name));
    }
}
