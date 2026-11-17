/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级表头映射注解：指定表头名与列序，读写两侧共用同一映射规则。
 *
 * <p>两个属性都取缺省时，字段仍参与映射（默认全部字段参与），表头名取字段名原样；
 * 表头名严格相等匹配，不做大小写折叠、不做驼峰与下划线互转。</p>
 *
 * <p>标注 {@code index} 且大于等于 0 时按列位置定位，优先于表头名。
 * 本注解不提供自定义转换器注册入口，仅支持模块内置常用类型。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelColumn {

    /**
     * 表头名；为空时退化为按字段名原样匹配表头。
     *
     * @return 表头名，缺省为空字符串
     */
    String value() default "";

    /**
     * 列序（从 0 起）；大于等于 0 时按列位置定位，优先于 {@link #value()}。
     *
     * @return 列序，缺省为 -1（表示未指定，按表头名定位）
     */
    int index() default -1;
}
