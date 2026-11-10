/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

/**
 * 容器实例获取与多例判定的唯一实现：任务实例与监听器共用同一判定语句，只是两个入口的失败语义不同。
 *
 * <p>语义差异（不可混淆）：任务实例取不到属<b>配置错误</b>，启动失败；监听器取不到属<b>正常</b>（观测为可选
 * 设施），返回 {@code null} 即可。两者都认为<b>多例 Bean 是配置错误</b>：任务状态与节拍会散落到不同实例上，
 * 监听器持有单一引用也失去意义。</p>
 */
public final class JobInstances {

    /**
     * 禁止创建工具类实例。
     */
    private JobInstances() {
    }

    /**
     * 取必需的实例：取不到即启动失败，多例亦启动失败。
     *
     * @param container 容器访问契约
     * @param type 类型
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @param <T> 类型参数
     * @return 容器内的单例实例，不为 {@code null}
     * @throws cn.hg.aifei.job.JobException 取不到实例或多例 Bean
     */
    public static <T> T requireSingle(JobContainer container, Class<T> type, String taskName) {
        T instance;
        try {
            instance = container.get(type);
        } catch (RuntimeException e) {
            throw JobErrors.instanceMissing(taskName, type.getName(), e);
        }
        if (instance == null) {
            throw JobErrors.instanceMissing(taskName, type.getName(), null);
        }
        if (!container.isSingleton(type)) {
            throw JobErrors.prototypeBean(taskName, type.getName());
        }
        return instance;
    }

    /**
     * 取可选的实例：取不到返回 {@code null}，多例即启动失败。
     *
     * @param container 容器访问契约
     * @param type 类型
     * @param <T> 类型参数
     * @return 容器内的单例实例；容器中没有该类型时返回 {@code null}
     * @throws cn.hg.aifei.job.JobException 该类型是多例 Bean
     */
    public static <T> T optionalSingle(JobContainer container, Class<T> type) {
        T instance;
        try {
            instance = container.get(type);
        } catch (RuntimeException e) {
            return null;
        }
        if (instance == null) {
            return null;
        }
        if (!container.isSingleton(type)) {
            throw JobErrors.listenerPrototype(type.getName());
        }
        return instance;
    }
}
