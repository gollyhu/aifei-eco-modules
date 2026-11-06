/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

import cn.hg.aifei.lock.internal.LockErrors;
import cn.hg.aifei.lock.internal.LockHolder;
import cn.hg.aifei.lock.internal.LockNames;
import cn.hg.aifei.lock.internal.LockStore;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 加锁入口：唯一创建 {@link Lock} 句柄的地方。
 *
 * <p>锁语义只依赖存储侧三条原子命令；参数校验先于装配检查，因此「参数错误」不会被「模块未装配」
 * 掩盖。抢不到锁返回 {@code null}（DEBUG），存储故障抛 {@link LockException}（ERROR），两者严格区分。</p>
 */
public final class LockKit {

    /**
     * 有限等待时的固定轮询间隔；无退避、无抖动、无配置。
     */
    private static final long RETRY_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    /**
     * 租约下限，与 cache 侧 TTL 校验对齐；上限由 cache 承担。
     */
    private static final Duration MIN_LEASE = Duration.ofMillis(1);

    /**
     * 禁止创建工具类实例。
     */
    private LockKit() {
    }

    /**
     * 立即尝试加锁，不等待。
     *
     * @param name 锁名
     * @param lease 租约时长，至少 1ms
     * @return 成功时返回句柄；抢不到时返回 {@code null}
     * @throws IllegalArgumentException 锁名为空、含空白、长度超过 128，或租约小于 1ms
     * @throws IllegalStateException 本模块未装配
     * @throws LockException 存储不可用、超时或序列化失败；不返回 {@code null}
     */
    public static Lock tryLock(String name, Duration lease) {
        return doTryLock(name, lease, Duration.ZERO);
    }

    /**
     * 有限等待加锁：先立即尝试一次，失败后按固定 50ms 间隔轮询。
     *
     * <p>等待时间用尽仍抢不到、或等待期间被中断时返回 {@code null}；等待期间任一次加锁调用抛
     * {@link LockException} 时立即上抛，不继续轮询。</p>
     *
     * @param name 锁名
     * @param lease 租约时长，至少 1ms
     * @param wait 最长等待时长，可为 0（等价于立即尝试）；不得为负
     * @return 成功时返回句柄；等待用尽或被中断时返回 {@code null}
     * @throws IllegalArgumentException 锁名非法、租约小于 1ms、{@code wait} 为 {@code null} 或为负
     * @throws IllegalStateException 本模块未装配
     * @throws LockException 存储不可用、超时或序列化失败；不返回 {@code null}
     */
    public static Lock tryLock(String name, Duration lease, Duration wait) {
        return doTryLock(name, lease, wait);
    }

    /**
     * 校验参数、取运行期存储并按需轮询。
     *
     * @param name 锁名
     * @param lease 租约时长
     * @param wait 最长等待时长
     * @return 成功时返回句柄；否则返回 {@code null}
     */
    private static Lock doTryLock(String name, Duration lease, Duration wait) {
        LockNames.requireValid(name);
        requireLease(lease);
        requireWait(wait);

        LockStore store = LockHolder.get();
        Lock lock = acquire(store, name, lease);
        if (lock != null || wait.isZero()) {
            return lock;
        }

        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return null;
            }
            if (!sleep(remaining)) {
                return null;
            }
            lock = acquire(store, name, lease);
            if (lock != null) {
                return lock;
            }
        }
    }

    /**
     * 生成标识并尝试一次加锁；存储异常按包装规则上抛。
     *
     * @param store 存储实现
     * @param name 锁名
     * @param lease 租约时长
     * @return 成功时返回句柄；已被他人持有时返回 {@code null}
     */
    private static Lock acquire(LockStore store, String name, Duration lease) {
        String token = LockNames.newToken();
        boolean acquired;
        try {
            acquired = store.tryAcquire(name, token, lease);
        } catch (RuntimeException e) {
            throw LockErrors.wrap("acquire", name, e);
        }
        return acquired ? new Lock(name, token, lease, store) : null;
    }

    /**
     * 睡眠不超过剩余等待时间。
     *
     * @param remaining 剩余等待纳秒数
     * @return 正常睡醒时 {@code true}；被中断时恢复中断位并返回 {@code false}
     */
    private static boolean sleep(long remaining) {
        try {
            TimeUnit.NANOSECONDS.sleep(Math.min(remaining, RETRY_INTERVAL_NANOS));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 校验租约下限；上限由 cache 的 TTL 校验承担并原样抛出 {@link IllegalArgumentException}。
     *
     * @param lease 租约时长
     */
    private static void requireLease(Duration lease) {
        if (lease == null) {
            throw new IllegalArgumentException("lease can not be null");
        }
        if (lease.compareTo(MIN_LEASE) < 0) {
            throw new IllegalArgumentException("lease must be at least 1ms");
        }
    }

    /**
     * 校验等待时长。
     *
     * @param wait 等待时长
     */
    private static void requireWait(Duration wait) {
        if (wait == null) {
            throw new IllegalArgumentException("wait can not be null");
        }
        if (wait.isNegative()) {
            throw new IllegalArgumentException("wait must not be negative");
        }
    }
}
