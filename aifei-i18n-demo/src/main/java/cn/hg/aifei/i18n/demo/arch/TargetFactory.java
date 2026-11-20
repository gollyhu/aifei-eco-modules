/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.arch;

import cn.aifei.aop.Aop;
import cn.aifei.proxy.InstanceFactory;
import cn.aifei.util.ComputeCache;
import java.util.function.Function;

/** TargetFactory：Service 目标对象工厂（单例缓存 + 依赖注入，demo 简化版）。 */
public class TargetFactory {

    boolean singleton;
    boolean injectDependency;
    InstanceFactory instanceFactory = new InstanceFactory();
    ComputeCache<Class<?>, Object> cache = new ComputeCache<>(512);

    public TargetFactory(boolean singleton, boolean injectDependency) {
        this.singleton = singleton;
        this.injectDependency = injectDependency;
    }

    /** 建议 target 类使用单例模式，避免每次请求创建 target 对象，提升性能。 */
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
