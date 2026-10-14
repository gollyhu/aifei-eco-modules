/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.demo.common.arch;

/**
 * 业务状态码：HTTP 恒 200，语义由 code 承载。0 为唯一成功，1xxxx 客户端、2xxxx 服务端、9xxxx 通用失败。
 */
public enum OutCode implements Code {

    OK(0, "OK"),
    FAIL(90000, "操作失败"),
    INVALID_PARAM(10001, "参数不合法"),
    UNAUTHORIZED(10002, "未授权"),
    FORBIDDEN(10003, "权限不足"),
    NOT_FOUND(10004, "资源不存在"),
    RATE_LIMIT(10006, "请求过于频繁"),
    SERVER_ERROR(20000, "服务端错误");

    private final int code;
    private final String msg;

    OutCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String msg() {
        return msg;
    }
}
