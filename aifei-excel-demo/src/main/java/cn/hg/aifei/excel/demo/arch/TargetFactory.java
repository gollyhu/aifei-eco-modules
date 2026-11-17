/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.excel.demo.arch;

import cn.aifei.aop.Aop;
import cn.aifei.proxy.InstanceFactory;
import cn.aifei.util.ComputeCache;
import java.util.function.Function;

/**
 * TargetFactory：Service 目标对象工厂（单例缓存 + 依赖注入，demo 简化版）。
 */
public class TargetFactory {

    // 是否单例
    boolean singleton;
    // 是否注入依赖
    boolean injectDependency;
    // 实例工厂
    InstanceFactory instanceFactory = new InstanceFactory();

    // 缓存 Target 对象（Java 8：显式 Function 形式）
    ComputeCache<Class<?>, Object> cache = new ComputeCache<>(512);

    /**
     * 构造方法
     *
     * @param singleton 是否单例
     * @param injectDependency 是否注入依赖
     */
    public TargetFactory(boolean singleton, boolean injectDependency) {
        this.singleton = singleton;
        this.injectDependency = injectDependency;
    }

    /**
     * 建议 target 类使用单例模式，避免每次请求创建 target 对象，提升性能。
     */
    public Object get(Class<?> type) {
        if (singleton) {
            return cache.computeIfAbsent(type, new Function<Class<?>, Object>() {
                @Override
                public Object apply(Class<?> t) {
                    Object ret = instanceFactory.get(t);
                    return injectDependency ? Aop.inject(ret) : ret;
                }
            });
        } else {
            Object ret = instanceFactory.get(type);
            return injectDependency ? Aop.inject(ret) : ret;
        }
    }
}
