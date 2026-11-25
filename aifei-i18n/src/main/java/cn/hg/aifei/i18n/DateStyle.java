/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

/**
 * 日期类输出的四档风格，与 JDK {@code java.text.DateFormat} 的 SHORT / MEDIUM / LONG / FULL
 * 一一对应（映射唯一定义点 = {@code internal.FormatSupport#toJdkStyle}）。
 *
 * <p>档位采用枚举编译期类型检查：四档之外的取值在编译期即被排除；
 * 模块不自定义档位语义、不提供中间档、不接受 pattern 透传；默认档位 MEDIUM 硬编码，不占配置键。</p>
 */
public enum DateStyle {
    SHORT, MEDIUM, LONG, FULL
}
