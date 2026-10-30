/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OutCode
 *
 * <pre>
 *  设计：
 *   1: 面向业务层，而非 HTTP 协议层，业务语义完全由 code 决定。
 *
 *   3: HTTP Status 始终 200。前端/网关/CDN 兼容性最好，避免中间件误判失败。
 *
 *   4: 使用 int 而非 String。值 0 为成功状态的唯一表达。前端判断规则：
 *      if (res.code === 0) {
 *          // 成功
 *      } else {
 *          // 错误分支可交由前端脚手架处理
 *          showError(res.msg)
 *      }
 *
 *   4: 编码值第一位数字 1、2、3 分别分类为："客户端错误"、"服务端错误"、"第三方错误"。
 *      10000、20000、30000 分别为通用的客户端、服务端、第三方错误。
 *
 *   5: 编码后四位中的前两位根据系统规模可用于表达项目或业务，后两位为具体的错误编码。
 *
 *   6: 其它所有枚举类统一使用 code、label 属性，生成前端 select 组件下拉列表项消除字典表及相关代码。
 *      OutCode 没有 label 属性不参与下拉列表项生成。
 *
 *   7: 枚举父类 Enum 已存在 name、name() 等成员，避免使用其作为枚举成员。
 * </pre>
 */
public enum OutCode implements Code {

    // 唯一正确状态码
    OK(0, "OK"),

    // 未分类通用失败：不区分责任域，仅用于快捷失败如 Out.fail(msg)，不应作为长期、精确错误使用
    FAIL(90000, "操作失败"),

    // Client errors (1xxxx)
    CLIENT_ERROR(10000, "客户端错误"),       // 兜底：请求不可处理/无法细分（仅框架层）
    INVALID_PARAM(10001, "参数不合法"),
    UNAUTHORIZED(10002, "未授权"),
    FORBIDDEN(10003, "权限不足"),
    NOT_FOUND(10004, "资源不存在"),
    CONFLICT(10005, "资源冲突"),            // 幂等、版本冲突、重复提交
    RATE_LIMIT(10006, "请求过于频繁"),

    // System errors (2xxxx)
    SERVER_ERROR(20000, "服务端错误"),     // 兜底：未捕获异常/框架内部错误（仅全局异常处理器）
    SERVICE_UNAVAILABLE(20001, "服务不可用"),
    DATA_ACCESS_ERROR(20002, "数据访问错误"),

    // External errors (3xxxx)
    EXTERNAL_ERROR(30000, "外部服务错误"),    // 兜底：依赖服务失败但无法细分（仅第三方封装层）
    EXTERNAL_UNAVAILABLE(30001, "外部服务不可用"),
    EXTERNAL_TIMEOUT(30002, "外部服务调用超时");

    private final int code;
    private final String msg;   // 其它枚举统一使用 label

    private static final String JSON = buildJson();
    private static final Map<Integer, OutCode> CACHE = Arrays.stream(values())
            .collect(Collectors.toMap(k -> k.code, v -> v));

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

    public static OutCode from(int code) {
        OutCode ret = CACHE.get(code);
        if (ret == null) {
            throw new IllegalArgumentException("Invalid OutCode code: " + code);
        }
        return ret;
    }

    private static String buildJson() {
        StringBuilder ret = new StringBuilder(800).append("{");
        boolean first = true;
        for (OutCode outCode : values()) {
            if (first) {
                first = false;
            } else {
                ret.append(',');
            }
            ret.append('"').append(outCode.name()).append("\":{")
                    .append("\"code\":").append(outCode.code()).append(',')
                    .append("\"msg\":\"").append(outCode.msg()).append('"')
                    .append('}');
        }
        return ret.append('}').toString();
    }

    /**
     * 转换 json 供前端缓存、使用，避免直接比较 int 值：
     *    const Code = JSON.parse(localStorage.getItem("Code"));
     *    if (response.code === Code.OK.code) {
     *        ...
     *    }
     */
    public static String json() {
        return JSON;
    }
}



