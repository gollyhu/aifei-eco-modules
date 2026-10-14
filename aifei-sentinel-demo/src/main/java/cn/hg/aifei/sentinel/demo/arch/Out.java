/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.arch;

import cn.aifei.argument.NoMatch;
import cn.aifei.core.Output;
import cn.aifei.db.transaction.RollbackDecision;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 响应对象：code/msg/data，code 为业务状态（0 成功），HTTP 恒 200 */
public class Out implements Output, NoMatch, RollbackDecision {

    int code;
    String msg;
    Object data;

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

    public static Out fail(Code code) {
        Objects.requireNonNull(code, "code can not be null.");
        return new Out(code.code(), code.msg());
    }

    public static Out of(Object data) {
        return new Out().data(data);
    }

    public static Out of(String field, Object value) {
        return new Out().set(field, value);
    }

    public static Out of() {
        return new Out();
    }

    public Out data(Object data) {
        this.data = data;
        return this;
    }

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

    @SuppressWarnings("unchecked")
    public <T> T get(String field) {
        return data instanceof Map ? (T) ((Map) data).get(field) : null;
    }

    @Override
    public boolean shouldRollback() {
        return code != OutCode.OK.code();
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
}
