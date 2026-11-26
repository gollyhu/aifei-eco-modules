/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo.arch;

/** 响应内容类型（demo 简化版）。 */
public enum ContentType {

    HTML("text/html; charset=utf-8"),
    JSON("application/json; charset=utf-8");

    public final String value;

    ContentType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
