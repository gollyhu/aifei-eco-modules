/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.aop.Aop;
import cn.aifei.proxy.InstanceFactory;
import cn.aifei.util.ComputeCache;

/**
 * TargetFactory
 */
public class TargetFactory {

    // 是否单例
    boolean singleton;
    // 是否注入依赖
    boolean injectDependency;
    // 实例工厂
    InstanceFactory instanceFactory = new InstanceFactory();

    // 缓存 Target 对象
    ComputeCache<Class<?>, Object> cache = new ComputeCache<>(512);

    /**
     * 构造方法
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
        // 需要通过注解 @Singleton(false) 支持指定类 "非单例" 时放开下面两行代码。getAnnotation 有一定性能损耗不建议使用
        // Singleton singletonAnn = type.getAnnotation(Singleton.class);
        // boolean singleton = singletonAnn != null ? singletonAnn.value() : this.singleton;

        if (singleton) {
            return cache.computeIfAbsent(type, t -> {
                Object ret = instanceFactory.get(t);
                return injectDependency ? Aop.inject(ret) : ret;
            });
        } else {
            Object ret = instanceFactory.get(type);
            return injectDependency ? Aop.inject(ret) : ret;
        }
    }
}



