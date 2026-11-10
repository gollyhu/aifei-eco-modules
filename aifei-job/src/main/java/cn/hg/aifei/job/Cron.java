/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按 cron 表达式触发任务。
 *
 * <p>表达式为 Quartz 语义：6 段含秒，可选第 7 段年；字段与特殊字符 {@code * , - / ? L W #} 全部按 Quartz
 * 语义解释。校验与下次触发计算一律委托 Quartz，不自实现解析器，也不做 5 段兼容写法（写错即启动失败）。</p>
 *
 * <p>被注解的方法必须是 {@code public} 实例方法、非 {@code static}、返回 {@code void}，参数为 {@code ()}
 * 或 {@code (JobEvent)}；同一方法只能有一个触发注解；继承而来的方法不注册。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Cron {

    /**
     * @return cron 表达式原文（6 段含秒，可选第 7 段年），必填且非空白
     */
    String value();
}
