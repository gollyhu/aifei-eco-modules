/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.id.IdException;
import java.time.Duration;

/**
 * 共享计数器来源：用 {@link Counter} 的原子自增取一个序号，再对 workerId 个数取模，得到 {@code 0..1023}
 * 的编号。取模规则是本规则的唯一定义点。
 *
 * <p>不需要 {@code aifei-lock}，因此比租约来源少一个模块依赖；代价是<b>回绕</b>：只有 TTL 窗口内的累计
 * 冷启动次数被计入，跨 TTL 或累计超过 1024 次时可能撞上仍在运行的实例。该风险由运维关注，模块不掩盖。</p>
 *
 * <p>无续期、无后台线程。首次 {@link #acquire()} 时自增取号并记住结果，后续调用返回同一编号——因此
 * {@code acquire()} 是幂等的，日志等诊断路径重复调用不会白白消耗编号。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class CacheWorkerIdSource implements WorkerIdSource {

    /**
     * 协同模块名，用于装配未就绪时的消息定位。
     */
    private static final String DEPENDENCY = "aifei-cache";

    /**
     * 计数值未取得时的哨兵，取值合法时必为 {@code 0..1023}。
     */
    private static final int UNRESOLVED = -1;

    /**
     * 共享计数器，由 {@link CacheAccess#requireSharedCounter()} 提供。
     */
    private final Counter counter;

    /**
     * 计数的有效期（毫秒），每次取号即续期（闲置过期语义）。
     */
    private final long ttlMillis;

    /**
     * 已取得的 workerId；{@link #UNRESOLVED} 表示尚未取号。
     */
    private volatile int workerId = UNRESOLVED;

    /**
     * 以共享计数器构造来源；构造过程不取号（取号发生在首次 {@link #acquire()}）。
     *
     * @param counter 主计数器，非空
     * @param ttlMillis 计数的有效期（毫秒）
     */
    public CacheWorkerIdSource(Counter counter, long ttlMillis) {
        this.counter = counter;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 取 workerId：首次调用时原子自增取号并取模，之后返回同一编号。
     *
     * @return workerId，取值 {@code 0..1023}
     * @throws IdException 计数器不可用
     */
    @Override
    public int acquire() {
        int resolved = workerId;
        if (resolved != UNRESOLVED) {
            return resolved;
        }
        synchronized (this) {
            if (workerId == UNRESOLVED) {
                workerId = resolve();
            }
            return workerId;
        }
    }

    /**
     * 计数器取号没有租约，恒返回 {@code true}。
     *
     * @return 恒为 {@code true}
     */
    @Override
    public boolean healthy() {
        return true;
    }

    /**
     * 空操作：计数来源没有需要释放的资源。
     */
    @Override
    public void close() {
        // 计数来源没有资源需要释放
    }

    /**
     * 自增取号并取模；用 {@link Math#floorMod(long, long)} 保证结果恒为非负的合法编号。
     *
     * @return workerId，取值 {@code 0..1023}
     * @throws IdException 计数器不可用
     */
    private int resolve() {
        long value;
        try {
            value = counter.increaseAndRefreshTtl(IdNames.CACHE_NAME, IdNames.WORKER_COUNTER_KEY,
                    1L, Duration.ofMillis(ttlMillis));
        } catch (IdException e) {
            throw e;
        } catch (RuntimeException e) {
            throw IdErrors.assemblyNotReady(DEPENDENCY, String.valueOf(e.getMessage()));
        }
        return (int) Math.floorMod(value - 1L, (long) IdNames.WORKER_ID_MODULUS);
    }
}
