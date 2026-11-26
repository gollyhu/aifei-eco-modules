/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.arch;

import cn.aifei.core.Output;
import java.util.LinkedHashMap;
import java.util.Map;

/** 统一响应信封：{@code code}/{@code msg}/{@code data}，HTTP 状态恒 200。 */
public class Out implements Output {

    int code;
    String msg;
    Object data;
    String view;

    public Out() {
    }

    Out(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static Out ok() {
        return new Out(OutCode.OK.code(), OutCode.OK.msg());
    }

    public static Out fail(String msg) {
        return new Out(OutCode.FAIL.code(), msg);
    }

    public static Out of(Object data) {
        return new Out().data(data);
    }

    /** 便捷构造：data 为单字段 map。 */
    public static Out of(String field, Object value) {
        return new Out().set(field, value);
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
            ((Map<String, Object>) data).put(field, value);
        } else {
            throw new IllegalStateException("Out 状态异常，data 必须为 Map 或 null");
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

    public String getMessage() {
        return msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
