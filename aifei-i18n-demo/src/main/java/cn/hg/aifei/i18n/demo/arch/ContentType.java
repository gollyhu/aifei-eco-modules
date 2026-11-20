/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.arch;

/** ContentType：响应内容类型枚举（demo 简化版，仅保留实际用到的两类）。 */
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
