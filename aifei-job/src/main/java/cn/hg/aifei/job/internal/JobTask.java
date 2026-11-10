/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.hg.aifei.job.JobEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 任务定义（不可变）：一个「业务方法 + 一个触发方式」的组合。
 *
 * <p>实例取自容器（代理对象，事务与日志生效），方法与触发方式在扫描期已校验，故本类在运行期不做校验、
 * 只负责调用。{@link #invoke(JobEvent)} 按参数个数选择 {@code ()} 或 {@code (JobEvent)} 两种调用形式，
 * 业务异常<b>不在本类吞掉</b>：统一由调用方解包 {@link InvocationTargetException} 后记账。</p>
 */
public final class JobTask {

    /**
     * 任务名，格式为「类全名#方法名」。
     */
    private final String name;

    /**
     * 任务所在类的实例（容器取出的代理对象）。
     */
    private final Object instance;

    /**
     * 任务方法。
     */
    private final Method method;

    /**
     * 触发方式。
     */
    private final JobTrigger trigger;

    /**
     * @param name 任务名，格式为「类全名#方法名」
     * @param instance 任务所在类的实例（容器取出的代理对象）
     * @param method 任务方法
     * @param trigger 触发方式
     */
    public JobTask(String name, Object instance, Method method, JobTrigger trigger) {
        this.name = name;
        this.instance = instance;
        this.method = method;
        this.trigger = trigger;
    }

    /**
     * @return 任务名，格式为「类全名#方法名」
     */
    public String name() {
        return name;
    }

    /**
     * @return 任务所在类的实例（容器取出的代理对象）
     */
    public Object instance() {
        return instance;
    }

    /**
     * @return 任务方法
     */
    public Method method() {
        return method;
    }

    /**
     * @return 触发方式
     */
    public JobTrigger trigger() {
        return trigger;
    }

    /**
     * 调用任务体：无参方法直接调用，单参方法传入本次的 {@code STARTED} 快照。
     *
     * @param startedEvent 本次执行的 {@link JobEvent.Phase#STARTED} 事件快照
     * @throws InvocationTargetException 任务体自身抛出异常时，异常被包装在此，由调用方解包记账
     * @throws ReflectiveOperationException 方法不可访问等反射故障；扫描期已校验 {@code public}，
     *         正常情况下不会出现
     */
    public void invoke(JobEvent startedEvent) throws ReflectiveOperationException {
        if (method.getParameterCount() == 0) {
            method.invoke(instance);
        } else {
            method.invoke(instance, startedEvent);
        }
    }
}
