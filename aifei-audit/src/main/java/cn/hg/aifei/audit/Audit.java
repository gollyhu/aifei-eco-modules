/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 采集声明：标注在业务方法上，方法每次执行产出一条审计事件。
 *
 * <p>注解属性只接受字面量（不支持表达式），且必须在运行期可读（{@code RUNTIME}）。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audit {

    /**
     * 动作码，必填且不得为空白；空串属启动期配置错误。
     *
     * @return 动作码
     */
    String action();

    /**
     * 人读描述，缺省为空串。
     *
     * @return 描述
     */
    String desc() default "";

    /**
     * 是否记录本方法入参（与全局 {@code ...args.enabled} 取 OR）。
     *
     * @return 记录入参时为 {@code true}
     */
    boolean args() default false;

    /**
     * 不记录原文的参数位（0-based 下标），越界下标在采集期忽略、不抛错。
     *
     * @return 被剔除的参数位
     */
    int[] mask() default {};
}
