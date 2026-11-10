/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.internal;

import cn.hg.aifei.job.Cron;
import cn.hg.aifei.job.FixedDelay;
import cn.hg.aifei.job.FixedRate;
import cn.hg.aifei.job.JobEvent;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 注解识别与方法签名校验的唯一定义点：把「候选类集合」翻译成「任务定义清单」。
 *
 * <p>只扫被扫到类<b>自身声明</b>的方法（{@code getDeclaredMethods()}）：继承而来的注解方法不注册，
 * 避免子类与父类各自注册而导致任务名判重误报。编译器生成的桥方法 / 合成方法直接跳过。</p>
 *
 * <p>本类<b>不做任务名判重</b>：同一方法被多个扫描包重复扫到时，判重在 {@link JobRegistry} 构造期完成，
 * 那里是判重的唯一定义点，重复即启动失败，不静默去重。</p>
 *
 * <p>失败一律经 {@link JobErrors} 抛启动期异常，消息携带任务名（即方法全名）、表达式原文或属性取值。</p>
 */
public final class JobScanner {

    /**
     * 签名规则描述常量：必须是 {@code public} 实例方法。
     */
    private static final String RULE_PUBLIC = "a public instance method";

    /**
     * 签名规则描述常量：不能是 {@code static}。
     */
    private static final String RULE_NON_STATIC = "a non-static method";

    /**
     * 签名规则描述常量：必须返回 {@code void}。
     */
    private static final String RULE_VOID = "return type void";

    /**
     * 签名规则描述常量：参数必须是 {@code ()} 或 {@code (JobEvent)}。
     */
    private static final String RULE_PARAMETERS = "parameters () or (JobEvent)";

    /**
     * 触发注解简单名：{@link Cron}。只在错误消息里使用。
     */
    private static final String NAME_CRON = "Cron";

    /**
     * 触发注解简单名：{@link FixedRate}。只在错误消息里使用。
     */
    private static final String NAME_FIXED_RATE = "FixedRate";

    /**
     * 触发注解简单名：{@link FixedDelay}。只在错误消息里使用。
     */
    private static final String NAME_FIXED_DELAY = "FixedDelay";

    /**
     * 容器访问契约，用于取任务所在类的实例。
     */
    private final JobContainer container;

    /**
     * cron 计算契约，只用于校验表达式。
     */
    private final CronCalculator cron;

    /**
     * 本次扫描的包清单，只用于「扫不到任务」时的错误消息。
     */
    private final List<String> scanPackages;

    /**
     * @param container 容器访问契约
     * @param cron cron 计算契约
     * @param scanPackages 本次扫描的包清单，用于错误消息定位
     */
    public JobScanner(JobContainer container, CronCalculator cron, Collection<String> scanPackages) {
        if (container == null || cron == null) {
            throw new NullPointerException("container and cron calculator must not be null");
        }
        this.container = container;
        this.cron = cron;
        this.scanPackages = scanPackages == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(scanPackages));
    }

    /**
     * 扫描候选类，产出任务定义清单。
     *
     * <p>每个方法的处理顺序固定：识别触发注解 → 多注解即失败 → 校验方法签名 → 校验注解属性并建立触发方式
     * → 校验 cron 表达式 → 从容器取单例实例。顺序即错误优先级，同一方法有多种违规时先报注解层面的问题。</p>
     *
     * @param candidates 候选类集合；为 {@code null} 视为空集合
     * @return 任务定义清单，保持候选类与方法的反射顺序，顺序不影响后续判重与排序
     * @throws cn.hg.aifei.job.JobException 多触发注解、签名非法、属性越界、表达式非法、实例取不到或多例、
     *         以及一个任务都没扫到
     */
    public List<JobTask> scan(Collection<Class<?>> candidates) {
        List<JobTask> tasks = new ArrayList<>();
        if (candidates != null) {
            for (Class<?> type : candidates) {
                if (type == null) {
                    continue;
                }
                for (Method method : type.getDeclaredMethods()) {
                    JobTask task = toTask(method);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }
        }
        if (tasks.isEmpty()) {
            throw JobErrors.noTaskFound(scanPackages);
        }
        return tasks;
    }

    /**
     * 把单个方法翻译成任务定义；不是任务方法时返回 {@code null}。
     *
     * @param method 候选方法
     * @return 任务定义；方法上没有触发注解时返回 {@code null}
     */
    private JobTask toTask(Method method) {
        if (method.isSynthetic() || method.isBridge()) {
            return null;
        }
        List<String> annotations = triggerAnnotations(method);
        if (annotations.isEmpty()) {
            return null;
        }
        String taskName = JobNames.of(method);
        if (annotations.size() > 1) {
            throw JobErrors.multipleTriggers(taskName, annotations);
        }
        validateSignature(method, taskName);
        JobTrigger trigger = buildTrigger(method, taskName);
        Object instance = JobInstances.requireSingle(container, method.getDeclaringClass(), taskName);
        return new JobTask(taskName, instance, method, trigger);
    }

    /**
     * 收集方法上的触发注解简单名。
     *
     * @param method 候选方法
     * @return 注解简单名列表，无触发注解时为空列表
     */
    private static List<String> triggerAnnotations(Method method) {
        List<String> names = new ArrayList<>(3);
        if (method.isAnnotationPresent(Cron.class)) {
            names.add(NAME_CRON);
        }
        if (method.isAnnotationPresent(FixedRate.class)) {
            names.add(NAME_FIXED_RATE);
        }
        if (method.isAnnotationPresent(FixedDelay.class)) {
            names.add(NAME_FIXED_DELAY);
        }
        return names;
    }

    /**
     * 校验方法签名：{@code public} 实例方法、非 {@code static}、返回 {@code void}、参数为 {@code ()} 或
     * {@code (JobEvent)}。
     *
     * @param method 候选方法
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     */
    private static void validateSignature(Method method, String taskName) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            throw JobErrors.illegalSignature(taskName, RULE_PUBLIC);
        }
        if (Modifier.isStatic(modifiers)) {
            throw JobErrors.illegalSignature(taskName, RULE_NON_STATIC);
        }
        if (method.getReturnType() != void.class) {
            throw JobErrors.illegalSignature(taskName, RULE_VOID);
        }
        if (!hasValidParameters(method)) {
            throw JobErrors.illegalSignature(taskName, RULE_PARAMETERS);
        }
    }

    /**
     * 判定参数形态是否为 {@code ()} 或 {@code (JobEvent)}。
     *
     * @param method 候选方法
     * @return 合法返回 {@code true}
     */
    private static boolean hasValidParameters(Method method) {
        int count = method.getParameterCount();
        if (count == 0) {
            return true;
        }
        return count == 1 && method.getParameterTypes()[0] == JobEvent.class;
    }

    /**
     * 按注解建立触发方式并校验：属性越界由 {@link JobTrigger} 的工厂方法负责，cron 表达式另经
     * {@link CronCalculator#validate(String, String)} 校验（表达式解析属 cron 计算契约，不在本类）。
     *
     * @param method 已通过签名校验的方法
     * @param taskName 任务名（类全名#方法名），用于错误消息定位
     * @return 触发方式值对象
     */
    private JobTrigger buildTrigger(Method method, String taskName) {
        Cron cronAnnotation = method.getAnnotation(Cron.class);
        if (cronAnnotation != null) {
            JobTrigger trigger = JobTrigger.cron(cronAnnotation.value(), taskName);
            cron.validate(trigger.cron(), taskName);
            return trigger;
        }
        FixedRate fixedRate = method.getAnnotation(FixedRate.class);
        if (fixedRate != null) {
            return JobTrigger.fixedRate(fixedRate.period(), fixedRate.initialDelay(), taskName);
        }
        FixedDelay fixedDelay = method.getAnnotation(FixedDelay.class);
        return JobTrigger.fixedDelay(fixedDelay.delay(), fixedDelay.initialDelay(), taskName);
    }
}
