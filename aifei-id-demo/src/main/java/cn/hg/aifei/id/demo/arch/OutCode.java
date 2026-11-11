/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo.arch;

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

    // 唯一正确状态码
    OK(0, "OK"),

    // 未分类通用失败：不区分责任域，仅用于快捷失败如 Out.fail(msg)
    FAIL(90000, "操作失败"),

    // 404：路由未命中
    NOT_FOUND(10004, "资源不存在"),

    // 500：未捕获异常
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
