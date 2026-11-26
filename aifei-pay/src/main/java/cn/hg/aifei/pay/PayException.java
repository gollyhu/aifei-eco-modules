/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay;

/**
 * 支付宝接入的统一异常：本模块对外暴露的**唯一**业务失败类型。
 *
 * <p>受检异常一律在内核边界收敛为本类型，故本类型 {@code extends RuntimeException}，
 * 调用方无需在被调用链上声明受检异常。</p>
 *
 * <p>{@link #code()} 透出内核错误码（如 {@code ACQ.TRADE_NOT_EXIST}），供业务按错误码分支；
 * 本模块自检类失败（配置非法、未装配、依赖缺失、验签失败）时该值为 {@code null}。</p>
 */
public class PayException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 内核错误码；本模块自检类失败时为 {@code null}。
     */
    private final String code;

    /**
     * 只带消息构造。
     *
     * @param message 脱敏后的可读消息
     */
    public PayException(String message) {
        this(message, null, null);
    }

    /**
     * 带消息与成因构造。
     *
     * @param message 脱敏后的可读消息
     * @param cause   原始异常，仅用于排查，不进入消息文本
     */
    public PayException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * 带消息、内核错误码与成因构造。
     *
     * @param message 脱敏后的可读消息
     * @param code    内核错误码，可为 {@code null}
     * @param cause   原始异常，仅用于排查，不进入消息文本
     */
    public PayException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 内核错误码。
     *
     * @return 内核错误码（如 {@code ACQ.TRADE_NOT_EXIST}）；本模块自检类失败时为 {@code null}
     */
    public String code() {
        return code;
    }
}
