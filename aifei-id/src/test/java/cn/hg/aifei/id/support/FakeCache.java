/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.internal.CounterFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 单测用缓存替身：只提供装配校验需要的两项声明——{@code supportsCas()} 的取值（由用例选定）与一个同源
 * 计数器，其余方法一律抛 {@link UnsupportedOperationException}。
 *
 * <p>用途：{@code id.worker.source=cache} 与 {@code id.segment.store=cache} 的装配分支要求
 * {@code CacheKit.use()} 可取到实例且后端支持条件操作。用例把本替身注册进 {@code CacheManager}，等价于
 * 「{@code CachePlugin} 已启动且接入共享后端」，于是取模与取段路径可以在零外部服务下走真实的装配代码；
 * {@link #notShared(Counter)} 则等价于「{@code CachePlugin} 已启动但后端只是本地缓存」，用于验证共享后端
 * 校验在启动期拒绝装配。</p>
 *
 * <p>行为刻意为窄：本模块只经由 {@code Counter} 取号，其余入口一旦被误用即立刻失败，而不是被替身的宽容
 * 实现掩盖。</p>
 */
public final class FakeCache {

    /**
     * 禁止创建工具类实例。
     */
    private FakeCache() {
    }

    /**
     * 创建「共享后端」缓存替身（{@code supportsCas() == true}）。
     *
     * @param counter 与该缓存同源的计数器，非空
     * @return 缓存替身
     */
    public static Cache shared(Counter counter) {
        return create(counter, true);
    }

    /**
     * 创建「非共享后端」缓存替身（{@code supportsCas() == false}，对应本地 Caffeine 后端）。
     *
     * @param counter 与该缓存同源的计数器，非空
     * @return 缓存替身
     */
    public static Cache notShared(Counter counter) {
        return create(counter, false);
    }

    /**
     * 以动态代理同时实现 {@link Cache} 与 {@link CounterFactory}。
     *
     * @param counter 同源计数器
     * @param shared 后端是否为共享存储（{@code supportsCas()} 的返回值）
     * @return 缓存替身
     */
    private static Cache create(final Counter counter, final boolean shared) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    if ("toString".equals(name)) {
                        return "FakeCache";
                    }
                    if ("hashCode".equals(name)) {
                        return Integer.valueOf(System.identityHashCode(proxy));
                    }
                    return Boolean.valueOf(proxy == args[0]);
                }
                if ("createCounter".equals(name)) {
                    return counter;
                }
                if ("supportsCas".equals(name)) {
                    return Boolean.valueOf(shared);
                }
                throw new UnsupportedOperationException("cache." + name);
            }
        };
        return (Cache) Proxy.newProxyInstance(
                FakeCache.class.getClassLoader(),
                new Class<?>[]{Cache.class, CounterFactory.class},
                handler);
    }
}
