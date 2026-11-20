/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.ipdb;

/**
 * IP 归属地统一异常，收敛参数非法、xdb 不可读、依赖缺失、解析失败等全部错误路径。
 */
public final class IpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造仅含消息的异常。
     *
     * @param message 可读错误消息
     */
    public IpException(String message) {
        super(message);
    }

    /**
     * 构造含消息与根因的异常。
     *
     * @param message 可读错误消息
     * @param cause 根因
     */
    public IpException(String message, Throwable cause) {
        super(message, cause);
    }
}
