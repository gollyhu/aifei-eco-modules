/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb.internal;

import cn.hg.aifei.tools.ipdb.IpException;

/**
 * ip 异常消息与构造的唯一定义点（设计 §7.2 措辞唯一来源）。
 */
public final class IpErrors {

    private IpErrors() {
    }

    /**
     * 参数为 null。
     *
     * @param method 方法名
     * @param name 参数名
     * @return 统一异常
     */
    public static IpException nullArgument(String method, String name) {
        return new IpException("ip: " + method + " argument " + name + " must not be null");
    }

    /**
     * v4/v6 路径都未提供。
     *
     * @return 统一异常
     */
    public static IpException noPath() {
        return new IpException("ip: at least one of v4/v6 xdb path must be provided");
    }

    /**
     * 非法 IP 文本。
     *
     * @param ip 原始 IP 文本
     * @param cause 内核抛出的地址异常
     * @return 统一异常
     */
    public static IpException invalidIp(String ip, Throwable cause) {
        return new IpException("ip: invalid ip address \"" + ip + "\"", cause);
    }

    /**
     * xdb 文件不可读。
     *
     * @param path xdb 路径
     * @param cause IO 异常
     * @return 统一异常
     */
    public static IpException unreadableXdb(String path, Throwable cause) {
        return new IpException("ip: xdb file not readable: " + path, cause);
    }

    /**
     * 可选依赖缺失。
     *
     * @param cause 链接错误
     * @return 统一异常
     */
    public static IpException missingDependency(Throwable cause) {
        return new IpException("ip: org.lionsoul:ip2region:3.3.7 is required, add it to your dependencies", cause);
    }

    /**
     * 会话已关闭后再查询。
     *
     * @return 统一异常
     */
    public static IpException closed() {
        return new IpException("ip: session is closed, create a new IpSearcher");
    }

    /**
     * region 解析失败。
     *
     * @param raw 原始 region 串
     * @param cause 原异常
     * @return 统一异常
     */
    public static IpException parseFailed(String raw, Throwable cause) {
        return new IpException("ip: failed to parse region \"" + raw + "\"", cause);
    }

    /**
     * 查询失败。
     *
     * @param ip 查询的 IP
     * @param cause 原异常
     * @return 统一异常
     */
    public static IpException searchFailed(String ip, Throwable cause) {
        return new IpException("ip: search failed for \"" + ip + "\", cause: " + detail(cause), cause);
    }

    /**
     * 关闭会话失败。
     *
     * @param cause 原异常
     * @return 统一异常
     */
    public static IpException closeFailed(Throwable cause) {
        return new IpException("ip: failed to close session, cause: " + detail(cause), cause);
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
