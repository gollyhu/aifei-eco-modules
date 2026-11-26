/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.arch;

/** 业务状态码：HTTP 恒 200，语义由 code 承载，0 为成功。 */
public enum OutCode {

    OK(0, "OK"),
    FAIL(90000, "操作失败"),
    NOT_FOUND(10004, "资源不存在"),
    SERVER_ERROR(20000, "服务端错误");

    private final int code;
    private final String msg;

    OutCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}
