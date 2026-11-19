/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.demo.arch;

/**
 * OutCode：业务状态码（demo 简化版，仅保留实际用到的四项）。
 *
 * <pre>
 * 设计：
 * 1: 面向业务层，而非 HTTP 协议层，业务语义完全由 code 决定。
 * 2: HTTP Status 始终 200，避免中间件误判失败；0 为成功状态的唯一表达。
 * </pre>
 */
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
