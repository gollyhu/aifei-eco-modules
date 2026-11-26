/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email;

/**
 * 模块统一异常（非受检）：所有失败路径的唯一出口，消息措辞由内部 {@code MailErrors} 统一构造。
 *
 * <p>发送失败一律抛本异常，不返回半截数据、不静默吞错；{@code cause} 保留底层原始原因
 * （SMTP 错误 / 网络异常 / 附件与模板 IO 失败），但消息正文不含第三方类型名。</p>
 */
public final class MailException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造无 cause 的异常。
     *
     * @param message 可读错误消息（统一以 {@code MailErrors} 构造）
     */
    public MailException(String message) {
        super(message);
    }

    /**
     * 构造保留 cause 的异常。
     *
     * @param message 可读错误消息（统一以 {@code MailErrors} 构造）
     * @param cause   原始异常（SMTP 错误 / 网络异常 / 附件与模板 IO 失败）
     */
    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}
