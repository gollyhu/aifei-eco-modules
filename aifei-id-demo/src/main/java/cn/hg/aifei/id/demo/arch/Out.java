/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo.arch;

import cn.aifei.core.Output;
import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Out：统一响应对象（demo 简化版）。
 *
 * <pre>
 * 契约：
 * 1: HTTP 恒 200，业务语义由 code 承载，0 为成功。
 * 2: 支持 view(...) 渲染 enjoy 模板，或 data(...) 输出 json。
 * </pre>
 */
public class Out implements Output {

    int code;
    String msg;
    Object data;

    String view;    // enjoy 引擎

    public Out() {}

    Out(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    // 有状态静态构造方法 ------------------------------------------------------------------------

    /**
     * 创建 out，状态置为成功
     */
    public static Out ok() {
        return new Out(OutCode.OK.code(), OutCode.OK.msg());
    }

    /**
     * 创建 out，状态置为成功并传入 msg
     */
    public static Out ok(String msg) {
        return new Out(OutCode.OK.code(), msg);
    }

    /**
     * 创建 out，状态置为成功并使用 MessageFormat 格式化 msg
     */
    public static Out ok(String format, Object... args) {
        return new Out(OutCode.OK.code(), MessageFormat.format(format, args));
    }

    /**
     * 创建 out，状态置为失败并传入 msg。不提供无参 fail() 方法，明确失败原因
     */
    public static Out fail(String msg) {
        return new Out(OutCode.FAIL.code(), msg);
    }

    /**
     * 创建 out，状态置为失败并使用 MessageFormat 格式化 msg
     */
    public static Out fail(String format, Object... args) {
        return new Out(OutCode.FAIL.code(), MessageFormat.format(format, args));
    }

    /**
     * 创建 out，状态置为失败并传入 OutCode
     */
    public static Out fail(OutCode outCode) {
        return new Out(outCode.code(), outCode.msg());
    }

    // 无状态静态构造方法 ------------------------------------------------------------------------

    /**
     * 创建 out 并传入 data 数据
     */
    public static Out of(Object data) {
        return new Out().data(data);
    }

    /**
     * 创建 out 并传入 field、value 数据
     */
    public static Out of(String field, Object value) {
        return new Out().set(field, value);
    }

    // 链式 setters -----------------------------------------------------------------------

    /**
     * 设置为成功状态
     */
    public Out setOk() {
        this.code = OutCode.OK.code();
        if (this.msg == null) {
            this.msg = OutCode.OK.msg();
        }
        return this;
    }

    /**
     * 设置为失败状态
     */
    public Out setFail() {
        this.code = OutCode.FAIL.code();
        if (this.msg == null) {
            this.msg = OutCode.FAIL.msg();
        }
        return this;
    }

    /**
     * 设置 msg
     */
    public Out msg(String msg) {
        this.msg = msg;
        return this;
    }

    /**
     * 设置 data
     */
    public Out data(Object data) {
        this.data = data;
        return this;
    }

    /**
     * 将 key、value 存入 data
     */
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

    /**
     * 设置 enjoy 模板
     */
    public Out view(String view) {
        this.view = view;
        return this;
    }

    // ----------------------------------------------------------------------------------

    // 供 json 转换用 getter、setter 方法 ------------------------------------------------------

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

    /**
     * 信封别名：id-demo 的设计约定字段名为 {@code message}，而 HIO 原字段名为 {@code msg}。
     * 二者恒同值、同出（JSON 中同时存在），前端按任一字段读取均可。
     */
    public String getMessage() {
        return msg;
    }
}
