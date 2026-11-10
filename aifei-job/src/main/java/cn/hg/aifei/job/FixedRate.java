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
 * 按固定频率触发任务：以上一次任务体<b>开始</b>执行的时刻为基准，加固定间隔得到下一个计划时刻。
 *
 * <p>任务耗时超过间隔时不重叠、不堆叠、不补跑：结束后按「上次开始 + 间隔」重算，已过去的计划点作一次跳过记录，
 * 并以当前时刻为新基准立即重排。</p>
 *
 * <p>被注解的方法必须是 {@code public} 实例方法、非 {@code static}、返回 {@code void}，参数为 {@code ()}
 * 或 {@code (JobEvent)}；同一方法只能有一个触发注解；继承而来的方法不注册。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface FixedRate {

    /**
     * @return 两次触发之间的间隔，单位毫秒，必须 {@code >= 1}；默认值 {@code 0} 视为未设置，启动失败
     */
    long period() default 0;

    /**
     * @return 首次触发延迟，单位毫秒，必须 {@code >= 0}；默认 {@code 0} 表示立即触发。只影响首次，
     *         之后不参与任何节拍计算
     */
    long initialDelay() default 0;
}
