/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms;

/**
 * 模块统一异常（非受检）：所有失败路径的唯一出口，消息措辞由内部 {@code SmsErrors} 统一构造。
 */
public final class SmsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造无 cause 的异常。
     *
     * @param message 可读错误消息
     */
    public SmsException(String message) {
        super(message);
    }

    /**
     * 构造保留 cause 的异常。
     *
     * @param message 可读错误消息
     * @param cause   原始异常（厂商错误 / 网络异常 / 解析失败）
     */
    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
