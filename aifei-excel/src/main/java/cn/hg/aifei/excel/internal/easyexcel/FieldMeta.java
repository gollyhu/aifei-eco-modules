/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.excel.internal.easyexcel;

import java.lang.reflect.Field;

/**
 * 字段元数据值对象：承载一个参与映射的字段的字段名、表头名、列序、Java 类型与反射句柄。
 *
 * <p>非承诺实现层类型，由 {@link RowMapper} 在构建时生成，读写两侧共用。</p>
 */
final class FieldMeta {

    private final String name;
    private final String header;
    private final int index;
    private final Class<?> javaType;
    private final Field field;

    FieldMeta(String name, String header, int index, Class<?> javaType, Field field) {
        this.name = name;
        this.header = header;
        this.index = index;
        this.javaType = javaType;
        this.field = field;
    }

    String name() {
        return name;
    }

    String header() {
        return header;
    }

    int index() {
        return index;
    }

    Class<?> javaType() {
        return javaType;
    }

    Field field() {
        return field;
    }
}
