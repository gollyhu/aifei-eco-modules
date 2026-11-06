/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.support;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.internal.CounterFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存替身工厂：以动态代理实现 {@link Cache} 与 {@link CounterFactory}，仅提供启动校验需要的能力声明，
 * 其余方法一律抛 {@link UnsupportedOperationException}。
 */
public final class FakeCaches {

    /**
     * 禁止创建工具类实例。
     */
    private FakeCaches() {
    }

    /**
     * 创建能力声明为指定值的主缓存替身。
     *
     * @param supportsCas 能力声明返回值
     * @param checks 能力查询计数；为 {@code null} 时不计数
     * @return 缓存替身
     */
    public static Cache capability(boolean supportsCas, AtomicInteger checks) {
        return create(supportsCas, checks, null);
    }

    /**
     * 创建能力查询自身抛异常的主缓存替身。
     *
     * @param error 能力查询要抛出的异常
     * @return 缓存替身
     */
    public static Cache failingCapability(RuntimeException error) {
        return create(false, null, error);
    }

    /**
     * 创建缓存替身。
     *
     * @param supportsCas 能力声明返回值
     * @param checks 能力查询计数
     * @param capabilityError 能力查询要抛出的异常；非空时优先于返回值
     * @return 缓存替身
     */
    private static Cache create(final boolean supportsCas, final AtomicInteger checks,
                                final RuntimeException capabilityError) {
        final Counter counter = (Counter) Proxy.newProxyInstance(
                FakeCaches.class.getClassLoader(),
                new Class<?>[]{Counter.class},
                new UnsupportedHandler("counter"));
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                String name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    if ("toString".equals(name)) {
                        return "FakeCache";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    return proxy == args[0];
                }
                if ("createCounter".equals(name)) {
                    return counter;
                }
                if ("supportsCas".equals(name)) {
                    if (capabilityError != null) {
                        throw capabilityError;
                    }
                    if (checks != null) {
                        checks.incrementAndGet();
                    }
                    return supportsCas;
                }
                throw new UnsupportedOperationException("cache." + name);
            }
        };
        return (Cache) Proxy.newProxyInstance(
                FakeCaches.class.getClassLoader(),
                new Class<?>[]{Cache.class, CounterFactory.class},
                handler);
    }

    /**
     * 除 Object 方法外一律不支持的处理器。
     */
    private static final class UnsupportedHandler implements InvocationHandler {

        private final String prefix;

        private UnsupportedHandler(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(method.getName())) {
                    return "Fake" + prefix;
                }
                if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                return proxy == args[0];
            }
            throw new UnsupportedOperationException(prefix + "." + method.getName());
        }
    }
}
