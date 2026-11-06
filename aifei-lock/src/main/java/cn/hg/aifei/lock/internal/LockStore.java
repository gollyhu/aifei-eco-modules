/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.internal;

import java.time.Duration;

/**
 * 锁存储的内部 SPI：三条原子操作是全部锁语义的来源。
 *
 * <p>非承诺 API。{@code token} 由 {@link LockNames#newToken()} 生成后作为入参传入，实现不得自行生成
 * ——它是 CAS 期望值的一部分，生成点必须唯一。</p>
 *
 * <p>返回值语义：{@code false} 表示确定的「未发生」（不存在、已过期或值不匹配）；存储不可用、超时或
 * 序列化失败必须抛运行时异常，不得吞成 {@code false}。</p>
 */
public interface LockStore {

    /**
     * 原子加锁：仅当锁名当前无人持有时写入 {@code token} 并设置租约。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @param lease 租约时长
     * @return 加锁成功时 {@code true}；已被他人持有（未过期）时 {@code false}
     */
    boolean tryAcquire(String name, String token, Duration lease);

    /**
     * 原子释放：当前值与 {@code token} 一致时删除锁。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @return 值匹配并删除成功时 {@code true}；不存在、已过期或值不匹配时 {@code false}
     */
    boolean release(String name, String token);

    /**
     * 原子续期：当前值与 {@code token} 一致时重设租约。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @param lease 新的租约时长
     * @return 值匹配并续期成功时 {@code true}；不存在、已过期或值不匹配时 {@code false}
     */
    boolean renew(String name, String token, Duration lease);
}
