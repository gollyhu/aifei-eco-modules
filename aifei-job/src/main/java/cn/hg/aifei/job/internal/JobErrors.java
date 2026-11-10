/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.hg.aifei.job.JobException;
import java.util.Collection;

/**
 * 启动期异常的唯一定义点：本类是全模块唯一出现 {@code new JobException} 的地方，其它类一律
 * {@code throw JobErrors.xxx(...)}。
 *
 * <p>每个方法<b>返回</b>异常对象而不是直接抛出，便于调用方决定是否附带 {@code cause}，
 * 也让「构造异常」与「在何处失败」两件事分离。消息一律携带定位信息：方法全名（即任务名
 * 「类全名#方法名」）、配置键名与原值、表达式原文。消息文本用英文，与仓库其它模块一致。</p>
 */
public final class JobErrors {

    /**
     * 禁止创建工具类实例。
     */
    private JobErrors() {
    }

    /**
     * 配置项取值非法。
     *
     * @param key 配置键名
     * @param value 配置原值
     * @param rule 期望规则描述
     * @return 异常对象，由调用方抛出
     */
    public static JobException configInvalid(String key, Object value, String rule) {
        return new JobException("job config '" + key + "' is invalid: " + value + ", required: " + rule);
    }

    /**
     * 配置项取值非法，携带根因（如数值解析失败）。
     *
     * @param key 配置键名
     * @param value 配置原值
     * @param rule 期望规则描述
     * @param cause 根因
     * @return 异常对象，由调用方抛出
     */
    public static JobException configInvalid(String key, Object value, String rule, Throwable cause) {
        return new JobException("job config '" + key + "' is invalid: " + value + ", required: " + rule, cause);
    }

    /**
     * 扫描包配置为空、缺失或含空项。
     *
     * @param key 配置键名
     * @param raw 配置原值
     * @return 异常对象，由调用方抛出
     */
    public static JobException scanPackagesEmpty(String key, String raw) {
        return new JobException("job config '" + key
                + "' must be a comma separated package list without blank item, but was: " + raw);
    }

    /**
     * 扫描包内没有任何任务。
     *
     * @param scanPackages 全部扫描包
     * @return 异常对象，由调用方抛出
     */
    public static JobException noTaskFound(Collection<String> scanPackages) {
        return new JobException("no job task found in scan packages: " + scanPackages);
    }

    /**
     * 任务方法签名非法（非 {@code public}、{@code static}、有返回值、参数不是 {@code ()} 或 {@code (JobEvent)}）。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param rule 期望规则描述
     * @return 异常对象，由调用方抛出
     */
    public static JobException illegalSignature(String taskName, String rule) {
        return new JobException("illegal job method " + taskName + ", required: " + rule);
    }

    /**
     * 同一方法声明了多个触发注解。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param annotations 该方法上出现的触发注解简单名
     * @return 异常对象，由调用方抛出
     */
    public static JobException multipleTriggers(String taskName, Collection<String> annotations) {
        return new JobException("job method " + taskName
                + " declares multiple trigger annotations: " + annotations);
    }

    /**
     * cron 表达式非法。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param expression 表达式原文
     * @param cause 根因，如表达式解析异常
     * @return 异常对象，由调用方抛出
     */
    public static JobException illegalCron(String taskName, String expression, Throwable cause) {
        return new JobException("illegal cron expression for job method " + taskName
                + ": " + expression, cause);
    }

    /**
     * 触发注解的属性越界。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param parameter 注解属性名
     * @param value 属性取值
     * @param rule 期望规则描述
     * @return 异常对象，由调用方抛出
     */
    public static JobException illegalParameter(String taskName, String parameter, long value, String rule) {
        return new JobException("illegal trigger parameter '" + parameter + "' = " + value
                + " for job method " + taskName + ", required: " + rule);
    }

    /**
     * 任务实例从容器取不到。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param typeName 任务所在类的全名
     * @param cause 根因，取实例时容器抛出的异常；无则传 {@code null}
     * @return 异常对象，由调用方抛出
     */
    public static JobException instanceMissing(String taskName, String typeName, Throwable cause) {
        String message = "can not get the instance of " + typeName + " for job method " + taskName
                + ", register it as a bean first";
        return cause == null ? new JobException(message) : new JobException(message, cause);
    }

    /**
     * 任务所在类是多例（prototype）Bean。
     *
     * @param taskName 任务名（类全名#方法名）
     * @param typeName 任务所在类的全名
     * @return 异常对象，由调用方抛出
     */
    public static JobException prototypeBean(String taskName, String typeName) {
        return new JobException("job method " + taskName + " is declared on a prototype bean: " + typeName
                + ", job tasks must be singleton beans");
    }

    /**
     * 监听器是多例（prototype）Bean。
     *
     * @param typeName 监听器实现类的全名
     * @return 异常对象，由调用方抛出
     */
    public static JobException listenerPrototype(String typeName) {
        return new JobException("job listener " + typeName + " must be a singleton bean");
    }

    /**
     * 同一任务名被注册两次（多个扫描包存在包含关系导致重复扫描）。
     *
     * @param taskName 任务名（类全名#方法名）
     * @return 异常对象，由调用方抛出
     */
    public static JobException duplicateTask(String taskName) {
        return new JobException("duplicate job task: " + taskName
                + ", check the tree of job.scan.packages for nested packages");
    }
}
