/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id;

/**
 * 模块唯一异常类型：覆盖启动期的配置 / 依赖 / 装配失败，以及运行期的不可用失败。
 *
 * <p><b>本异常由模块内部抛出，业务方只 {@code catch}、不要 {@code new}。</b>构造器为 {@code public}
 * 是因为唯一构造点 {@link cn.hg.aifei.id.internal.IdErrors} 与公共类型不同包（Java 包私有语义所限），
 * 并非对外构造入口；消息必然携带定位信息（配置键与原值 / workerId / 键名 / 时间戳）。</p>
 *
 * <p>覆盖<b>启动期</b>全部失败：{@code id.*} 配置非法、数值非整数、必填缺失、可选依赖缺失、缓存后端
 * 非共享、workerId 全被占用。覆盖<b>运行期</b>全部失败：时钟回拨超阈、时钟早于 EPOCH、时间戳溢出、
 * 等待时钟被中断、号段取段失败、号段回退、workerId 租约丢失。</p>
 *
 * <p>语义分层：调用方写错配置用 {@link IllegalArgumentException}（仅
 * {@code new IdPlugin(null)} 一处）、调用时机错用 {@link IllegalStateException}（仅 {@code IdKit}
 * 未装配一处）、模块自身不可用用本异常。运行期永不降级：不返回 {@code 0}、不回退本地、不静默重试。</p>
 */
public final class IdException extends RuntimeException {

    /**
     * 以故障描述构造异常，用于不携带根因的场景（配置校验、状态判定等）。
     *
     * @param message 故障描述，必然含定位信息（配置键与原值 / workerId / 键名 / 时间戳）
     */
    public IdException(String message) {
        super(message);
    }

    /**
     * 以故障描述与根因构造异常，用于底层存储或协同模块抛错的场景。
     *
     * @param message 故障描述，必然含定位信息（配置键与原值 / workerId / 键名 / 时间戳）
     * @param cause 根因，如缓存计数器或锁模块抛出的运行时异常
     */
    public IdException(String message, Throwable cause) {
        super(message, cause);
    }
}
