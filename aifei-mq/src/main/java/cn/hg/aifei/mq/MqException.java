/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 消息能力的唯一未受检异常，用于运行时故障（如真实后端连接失败）。
 *
 * <p>参数非法与状态非法分别由 {@link IllegalArgumentException} / {@link IllegalStateException} 承担，
 * 本异常不细分连接、超时等子类。</p>
 */
public class MqException extends RuntimeException {

    /**
     * 由故障描述创建异常。
     *
     * @param message 故障描述
     */
    public MqException(String message) {
        super(message);
    }

    /**
     * 由故障描述与根因创建异常。
     *
     * @param message 故障描述
     * @param cause 根因
     */
    public MqException(String message, Throwable cause) {
        super(message, cause);
    }
}
