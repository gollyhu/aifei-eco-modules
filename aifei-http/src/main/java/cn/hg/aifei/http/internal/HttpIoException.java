/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;

/**
 * 网络层失败标记：连接拒绝、connect / read 超时、读流中断等可重试的传输失败的唯一构造点。
 *
 * <p>仅服务 {@link HttpRetry#shouldRetryOnFailure(HttpException)} 的判别，<b>不作为公共面签名</b>：
 * 调用方一律按 {@link HttpException} 捕获。</p>
 */
public final class HttpIoException extends HttpException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造网络层失败，尝试次数记为 1。
     *
     * @param message 失败描述
     */
    public HttpIoException(String message) {
        super(message);
    }

    /**
     * 构造网络层失败并保留底层原因，尝试次数记为 1。
     *
     * @param message 失败描述
     * @param cause   底层原因，可为 {@code null}
     */
    public HttpIoException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造网络层失败并显式指定尝试次数。
     *
     * @param message  失败描述
     * @param cause    底层原因，可为 {@code null}
     * @param attempts 本次调用共尝试几次
     */
    public HttpIoException(String message, Throwable cause, int attempts) {
        super(message, cause, attempts);
    }
}
