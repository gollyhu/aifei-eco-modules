/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * demo 侧的文本格式化小工具：时间戳与异常摘要。
 *
 * <p>时间格式固定为 {@code HH:mm:ss.SSS}（秒级任务看毫秒差才有意义）；异常摘要只取「简单类名: message」，
 * 完整堆栈由模块自己经日志记录，端点不搬运堆栈文本。</p>
 */
public final class DemoFormats {

    /**
     * 时刻格式（本地时区）。
     */
    private static final String TIME_PATTERN = "HH:mm:ss.SSS";

    /**
     * 禁止创建工具类实例。
     */
    private DemoFormats() {
    }

    /**
     * 把 epoch 毫秒格式化为 {@code HH:mm:ss.SSS}。
     *
     * @param epochMillis epoch 毫秒；非正数返回 {@code "—"}
     * @return 时刻文本
     */
    public static String clockText(long epochMillis) {
        if (epochMillis <= 0L) {
            return "—";
        }
        // SimpleDateFormat 非线程安全：每次新建，监听器回调与 Service 线程可并发
        return new SimpleDateFormat(TIME_PATTERN).format(new Date(epochMillis));
    }

    /**
     * 取异常摘要：{@code 简单类名: message}。
     *
     * @param error 异常，可为 {@code null}
     * @return 摘要文本；{@code error} 为 {@code null} 时返回 {@code null}
     */
    public static String errorText(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        String type = error.getClass().getName();
        return message == null || message.trim().isEmpty() ? type : type + ": " + message;
    }
}
