/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.annotation;

import java.lang.annotation.*;

/** 声明方法级逻辑资源名；未标注或 value 空白时回退默认「全限定类名#方法名」。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SentinelResource {
    String value();
}
