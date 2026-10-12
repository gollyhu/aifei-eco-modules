/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.demo.arch;

import cn.aifei.aop.Aop;
import cn.aifei.proxy.InstanceFactory;
import cn.aifei.util.ComputeCache;

/** 管理 Controller（Service）实例的创建与缓存 */
public class TargetFactory {

    private final boolean singleton;
    private final boolean injectDependency;
    private final InstanceFactory instanceFactory = new InstanceFactory();
    private final ComputeCache<Class<?>, Object> cache = new ComputeCache<>(512);

    public TargetFactory(boolean singleton, boolean injectDependency) {
        this.singleton = singleton;
        this.injectDependency = injectDependency;
    }

    public Object get(Class<?> type) {
        if (singleton) {
            return cache.computeIfAbsent(type, t -> {
                Object ret = instanceFactory.get(t);
                return injectDependency ? Aop.inject(ret) : ret;
            });
        }
        Object ret = instanceFactory.get(type);
        return injectDependency ? Aop.inject(ret) : ret;
    }
}
