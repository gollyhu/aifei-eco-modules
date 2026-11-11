/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.support;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;
import cn.aifei.log.LogKit;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 测试用日志工厂：让模块唯一的记录点（「将重试」时的 warn）在无日志绑定的测试环境里可用。
 *
 * <p>生产环境的 {@code LogFactory} 由应用启动流程装配；单测不引入任何日志绑定，故在此换入空实现，
 * 用例结束后还原，避免影响其它测试类。</p>
 */
public final class NoOpLogFactory implements LogFactory {

    private static final Log NO_OP = newNoOpLog();

    /**
     * 换入空实现。
     *
     * @return 换入前的工厂，供 {@link #restore(LogFactory)} 还原
     */
    public static LogFactory install() {
        LogFactory previous = LogKit.get().getLogFactory();
        LogKit.get().setLogFactory(new NoOpLogFactory());
        return previous;
    }

    /**
     * 还原工厂。
     *
     * @param previous 换入前的工厂
     */
    public static void restore(LogFactory previous) {
        LogKit.get().setLogFactory(previous);
    }

    @Override
    public Log getLog() {
        return NO_OP;
    }

    @Override
    public Log getLog(Class<?> clazz) {
        return NO_OP;
    }

    @Override
    public Log getLog(String name) {
        return NO_OP;
    }

    /**
     * 以动态代理构造空实现：布尔询问返回 {@code false}，其余返回 {@code null}，不产生任何输出。
     *
     * @return 空实现日志
     */
    private static Log newNoOpLog() {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) {
                    return Boolean.FALSE;
                }
                if (returnType == String.class) {
                    return "no-op";
                }
                return null;
            }
        };
        return (Log) Proxy.newProxyInstance(Log.class.getClassLoader(), new Class<?>[]{Log.class}, handler);
    }
}
