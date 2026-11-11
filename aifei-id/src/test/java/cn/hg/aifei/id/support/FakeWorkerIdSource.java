/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.hg.aifei.id.internal.WorkerIdSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单测用 workerId 来源：编号与「租约是否有效」都可编排，用来把雪花算法的健康检查路径变成可控输入。
 *
 * <p>{@code 0..1023} 的合法编号、越界编号（用于验证雪花构造期拒绝）、租约丢失（{@link #breakLease()}）
 * 三种情形都由此桩提供。{@code id.worker.source=lock} 的真实抢占路径依赖 {@code aifei-lock} 与共享后端，
 * 不在本批单测范围内。</p>
 *
 * <p>{@link #acquire()} 计数便于断言「诊断路径重复取号不会重复消耗编号」。</p>
 */
public final class FakeWorkerIdSource implements WorkerIdSource {

    /**
     * 向雪花算法声明的编号；允许越界，用于验证构造期校验。
     */
    private final int workerId;

    /**
     * {@link #acquire()} 的调用次数。
     */
    private final AtomicInteger acquireCount = new AtomicInteger();

    /**
     * 租约是否有效。
     */
    private volatile boolean healthy = true;

    /**
     * 是否已关闭。
     */
    private volatile boolean closed = false;

    /**
     * 以指定编号构造来源。
     *
     * @param workerId 声明的 workerId，可越界（由被测代码负责拒绝）
     */
    public FakeWorkerIdSource(int workerId) {
        this.workerId = workerId;
    }

    /**
     * 模拟租约丢失：此后 {@link #healthy()} 返回 {@code false}。
     *
     * @return 本对象，便于链式构造
     */
    public FakeWorkerIdSource breakLease() {
        this.healthy = false;
        return this;
    }

    /**
     * 返回构造时声明的编号，并累计调用次数。
     *
     * @return workerId
     */
    @Override
    public int acquire() {
        acquireCount.incrementAndGet();
        return workerId;
    }

    /**
     * 返回租约是否有效：未丢失且未关闭时为 {@code true}。
     *
     * @return 租约有效时 {@code true}
     */
    @Override
    public boolean healthy() {
        return healthy && !closed;
    }

    /**
     * 标记关闭；重复调用无副作用。
     */
    @Override
    public void close() {
        closed = true;
    }

    /**
     * 返回 {@link #acquire()} 被调用的次数。
     *
     * @return 调用次数
     */
    public int acquireCount() {
        return acquireCount.get();
    }

    /**
     * 返回是否已关闭。
     *
     * @return 已关闭时 {@code true}
     */
    public boolean isClosed() {
        return closed;
    }
}
