/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描范围展开与空动作码校验（非承诺 API）：扫描范围由消费方给出，模块不自造。
 *
 * <p>包扫描经 {@link AuditEnvironment} 端口，本类零框架引用。</p>
 */
public final class AuditScanner {

    private static final String RULE_CLASS = "每项须为全限定类名";

    private AuditScanner() {
    }

    /**
     * 启动期校验：合并扫描范围并断言范围内不存在空动作码的标注方法。
     *
     * @param config        配置
     * @param extraPackages 代码追加的包名，可为 null
     * @param extraClasses  代码追加的类，可为 null
     * @param env           框架能力端口（包扫描）
     * @throws cn.hg.aifei.audit.AuditException 未提供扫描范围 / 类加载失败 / 存在空动作码
     */
    public static void validate(AuditConfig config, List<String> extraPackages, List<Class<?>> extraClasses,
                                AuditEnvironment env) {
        Set<String> packages = new LinkedHashSet<String>();
        packages.addAll(config.scanPackages());
        if (extraPackages != null) {
            for (String pkg : extraPackages) {
                if (pkg != null && !pkg.trim().isEmpty()) {
                    packages.add(pkg.trim());
                }
            }
        }
        List<String> classNames = config.scanClasses();
        boolean hasExtraClasses = extraClasses != null && !extraClasses.isEmpty();
        if (packages.isEmpty() && classNames.isEmpty() && !hasExtraClasses) {
            throw AuditErrors.scanRangeMissing();
        }

        Set<Class<?>> targets = new LinkedHashSet<Class<?>>();
        for (String pkg : packages) {
            targets.addAll(env.scan(pkg));
        }
        for (String className : classNames) {
            Class<?> clazz = SafeInvoker.loadClass(className);
            if (clazz == null) {
                throw AuditErrors.configInvalid(AuditConfig.SCAN_CLASSES_KEY, className, RULE_CLASS);
            }
            targets.add(clazz);
        }
        if (extraClasses != null) {
            for (Class<?> clazz : extraClasses) {
                if (clazz != null) {
                    targets.add(clazz);
                }
            }
        }
        for (Class<?> target : targets) {
            validateClass(target);
        }
    }

    private static void validateClass(Class<?> target) {
        Method[] methods = target.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            AuditAnnotations.ActionSpec spec = AuditAnnotations.of(methods[i]);
            if (spec == null) {
                continue;
            }
            String action = spec.getAction();
            if (action == null || action.trim().isEmpty()) {
                throw AuditErrors.emptyAction(target.getName() + "#" + methods[i].getName());
            }
        }
    }
}
