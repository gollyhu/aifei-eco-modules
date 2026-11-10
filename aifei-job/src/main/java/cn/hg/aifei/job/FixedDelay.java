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
 * 按固定延迟触发任务：以上一次任务体<b>结束</b>的时刻为基准，加固定延迟得到下一个计划时刻。
 *
 * <p>与 {@link FixedRate} 共用执行链，仅「下次触发延迟」的计算不同；因此任务耗时天然计入间隔，
 * 不存在跳过判定。</p>
 *
 * <p>被注解的方法必须是 {@code public} 实例方法、非 {@code static}、返回 {@code void}，参数为 {@code ()}
 * 或 {@code (JobEvent)}；同一方法只能有一个触发注解；继承而来的方法不注册。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface FixedDelay {

    /**
     * @return 上次结束到下次触发的延迟，单位毫秒，必须 {@code >= 1}；默认值 {@code 0} 视为未设置，启动失败
     */
    long delay() default 0;

    /**
     * @return 首次触发延迟，单位毫秒，必须 {@code >= 0}；默认 {@code 0} 表示立即触发。只影响首次，
     *         之后不参与任何节拍计算
     */
    long initialDelay() default 0;
}
