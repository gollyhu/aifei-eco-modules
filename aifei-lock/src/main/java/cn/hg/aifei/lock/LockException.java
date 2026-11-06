/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

/**
 * 锁操作在存储侧失败时抛出的运行时异常，语义是「结果未知」而非「锁已丢失」。
 *
 * <p>存储不可用、超时或序列化失败都会抛它：此时无法判断加锁 / 释放 / 续期是否已经生效，
 * 调用方可重试（CAS 操作重试安全），也可放弃并由租约自然过期兜底。只有 {@link Lock#renew()}
 * 返回 {@code false} 才是「确认丢失」。公共方法不声明 {@code throws}。</p>
 */
public class LockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 用指定消息创建异常。
     *
     * @param message 异常消息
     */
    public LockException(String message) {
        super(message);
    }

    /**
     * 用指定消息与根因创建异常。
     *
     * @param message 异常消息
     * @param cause 根因，可为 {@code null}
     */
    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}
