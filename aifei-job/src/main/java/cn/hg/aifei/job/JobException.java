/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

/**
 * 启动期失败的唯一异常类型：配置非法、扫描不到任务、注解用法或方法签名非法、cron 表达式非法、
 * 节拍参数越界、任务实例取不到或为多例、监听器为多例等，一律以本异常抛出，不另用
 * {@link IllegalArgumentException} / {@link IllegalStateException} 表达。
 *
 * <p><b>本异常由模块内部抛出，业务方只 {@code catch}、不要 {@code new}。</b>构造器为 {@code public}
 * 是因为唯一构造点 {@code internal.JobErrors} 与公共类型不同包（Java 包私有语义所限），并非对外构造入口；
 * 消息必然携带定位信息（方法全名 / 键名与原值 / 表达式原文 / 任务名），底层异常（如 cron 表达式解析异常）
 * 保留为 {@link #getCause()}。</p>
 *
 * <p>本异常只在启动期出现：任务体异常、提交被拒、监听器回调异常一律就地捕获记录，运行期不外抛本异常。</p>
 */
public final class JobException extends RuntimeException {

    /**
     * @param message 故障描述，含定位信息
     */
    public JobException(String message) {
        super(message);
    }

    /**
     * @param message 故障描述，含定位信息
     * @param cause 根因
     */
    public JobException(String message, Throwable cause) {
        super(message, cause);
    }
}
