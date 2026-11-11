/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

/**
 * 出站 HTTP 的唯一未受检异常：传输层失败、配置非法、未装配、响应体超限、解码失败、重试拒绝统一用它表达，
 * 底层原因保留在 {@link #getCause()}。
 *
 * <p>不细分子类到公共面：调用方只需一个 catch 点。非法入参（空白名称、负数、{@code null}）属调用方当场
 * 写错的即时可判错误，抛 {@link IllegalArgumentException}。</p>
 */
public class HttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 本次调用共尝试几次，最小为 1。
     */
    private final int attempts;

    /**
     * 构造失败信息，尝试次数记为 1。
     *
     * @param message 失败描述
     */
    public HttpException(String message) {
        this(message, null, 1);
    }

    /**
     * 构造失败信息并保留底层原因，尝试次数记为 1。
     *
     * @param message 失败描述
     * @param cause   底层原因，可为 {@code null}
     */
    public HttpException(String message, Throwable cause) {
        this(message, cause, 1);
    }

    /**
     * 构造失败信息并显式指定尝试次数。
     *
     * @param message  失败描述
     * @param cause    底层原因，可为 {@code null}
     * @param attempts 本次调用共尝试几次
     * @throws IllegalArgumentException {@code attempts} 小于 1
     */
    public HttpException(String message, Throwable cause, int attempts) {
        super(message, cause);
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts 必须 >= 1，实际为 " + attempts);
        }
        this.attempts = attempts;
    }

    /**
     * 本次调用共尝试几次（含首次尝试）。
     *
     * @return 尝试次数，最小为 1
     */
    public int attempts() {
        return attempts;
    }
}
