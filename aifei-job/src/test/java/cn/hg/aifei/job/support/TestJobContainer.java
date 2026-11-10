/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.support;

import cn.hg.aifei.job.internal.JobContainer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link JobContainer} 的测试替身：按预置的「类型 → 实例」返回，按预置的多例类型集合判定单例语义。
 *
 * <p>用它把扫描与实例解析从框架容器上摘下来：用例可以精确构造「取不到」（未注册）、「容器抛异常」
 * （{@link #failOnGet()}）与「多例」三种情形，而不需要启动 aifei 容器。</p>
 */
public final class TestJobContainer implements JobContainer {

    /**
     * 预置的类型到实例的映射。
     */
    private final Map<Class<?>, Object> instances = new LinkedHashMap<>();

    /**
     * 预置的多例类型集合。
     */
    private final Set<Class<?>> prototypeTypes = new LinkedHashSet<>();

    /**
     * 是否模拟容器取实例时抛异常。
     */
    private boolean failOnGet;

    /**
     * 注册一个单例实例。
     *
     * @param type 类型
     * @param instance 实例
     * @return 本替身，便于链式装配
     */
    public TestJobContainer register(Class<?> type, Object instance) {
        instances.put(type, instance);
        return this;
    }

    /**
     * 把类型标记为多例 Bean。
     *
     * @param type 类型
     * @return 本替身，便于链式装配
     */
    public TestJobContainer prototype(Class<?> type) {
        prototypeTypes.add(type);
        return this;
    }

    /**
     * 让取实例的行为改为抛异常，用于验证「容器抛异常即视为取不到」。
     *
     * @return 本替身，便于链式装配
     */
    public TestJobContainer failOnGet() {
        this.failOnGet = true;
        return this;
    }

    @Override
    public <T> T get(Class<T> type) {
        if (failOnGet) {
            throw new IllegalStateException("container is not ready for " + type.getName());
        }
        return type.cast(instances.get(type));
    }

    @Override
    public boolean isSingleton(Class<?> type) {
        return !prototypeTypes.contains(type);
    }
}
