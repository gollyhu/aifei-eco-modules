/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.arch;

import cn.aifei.core.Output;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Out：统一响应对象（demo 简化版）。
 *
 * <p>本类属从其它 demo 复制的通用脚手架，职责是以信封 {@code code} 承载业务语义、统一输出
 * view（enjoy 模板）或 json；仅供 demo 演示，不含生产级错误处理。以下链式设置方法语义同方法名。</p>
 *
 * <ol>
 *   <li>HTTP 恒 200，业务语义由 code 承载，0 为成功。</li>
 *   <li>支持 view(...) 渲染 enjoy 模板，或 data(...) 输出 json。</li>
 * </ol>
 */
public class Out implements Output {

    int code;
    String msg;
    Object data;
    String view;

    public Out() {}

    Out(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static Out ok() {
        return new Out(OutCode.OK.code(), OutCode.OK.msg());
    }

    public static Out ok(String msg) {
        return new Out(OutCode.OK.code(), msg);
    }

    public static Out ok(String format, Object... args) {
        return new Out(OutCode.OK.code(), MessageFormat.format(format, args));
    }

    public static Out fail(String msg) {
        return new Out(OutCode.FAIL.code(), msg);
    }

    public static Out fail(String format, Object... args) {
        return new Out(OutCode.FAIL.code(), MessageFormat.format(format, args));
    }

    public static Out fail(OutCode outCode) {
        return new Out(outCode.code(), outCode.msg());
    }

    public static Out of(Object data) {
        return new Out().data(data);
    }

    public static Out of(String field, Object value) {
        return new Out().set(field, value);
    }

    public Out msg(String msg) {
        this.msg = msg;
        return this;
    }

    public Out data(Object data) {
        this.data = data;
        return this;
    }

    @SuppressWarnings("unchecked")
    public Out set(String field, Object value) {
        if (data instanceof Map) {
            ((Map<String, Object>) data).put(field, value);
        } else if (data == null) {
            data = new LinkedHashMap<String, Object>();
            ((LinkedHashMap<String, Object>) data).put(field, value);
        } else {
            throw new IllegalStateException("Out 状态异常，data 必须为 Map 类型或 null");
        }
        return this;
    }

    public Out view(String view) {
        this.view = view;
        return this;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMessage() {
        return msg;
    }
}
