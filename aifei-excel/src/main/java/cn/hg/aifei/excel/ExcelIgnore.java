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
 * 字段级排除注解：标注字段在读写两侧都完全排除。
 *
 * <p>被标注字段不参与表头映射、不占用列序、不出现在导出列；即使同时标注了
 * {@link ExcelColumn}，本注解优先，字段仍被排除。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelIgnore {
}
