/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.id.IdException;
import cn.hg.aifei.lock.Lock;
import cn.hg.aifei.lock.LockKit;
import java.time.Duration;

/**
 * 锁租约来源：按 {@code 0..1023} 升序尝试 {@link LockKit#tryLock(String, Duration)}，第一个抢到的即为
 * 本实例的 workerId，并在进程存活期长期持有、由唯一的后台线程续期。
 *
 * <p>本类是全模块<b>唯一创建线程</b>的地方（线程名 {@code aifei-id-worker-lease}，daemon，周期
 * {@code max(1000ms, lease/3)}）。续期失败（{@link Lock#renew()} 返回 {@code false} 或抛异常）即把
 * {@link #healthy()} 置为 {@code false}：租约丢失后继续发号必然重号，且不可逆，因此<b>不自愈</b>，
 * 由雪花算法在下一次发号时显式失败。</p>
 *
 * <p>抢不到任何 workerId 直接启动失败，不等待——租约按进程生命周期持有，等待通常无意义，快速失败更利于
 * 运维定位。</p>
 *
 * <p>依赖 {@code aifei-lock} 与 {@code aifei-cache}，且 {@code LockPlugin} 必须晚于 {@code CachePlugin}、
 * 早于本模块装配。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class LockWorkerIdSource implements WorkerIdSource {

    /**
     * 续期线程名，便于线程栈与诊断工具定位。
     */
    private static final String THREAD_NAME = "aifei-id-worker-lease";

    /**
     * 续期周期下限（毫秒），避免短租约把线程唤醒得过频。
     */
    private static final long MIN_RENEW_INTERVAL_MILLIS = 1000L;

    /**
     * 停机时等待续期线程结束的上限（毫秒）。
     */
    private static final long CLOSE_JOIN_MILLIS = 1000L;

    /**
     * 协同模块名，用于装配未就绪时的消息定位。
     */
    private static final String DEPENDENCY = "aifei-lock";

    /**
     * 已持有的租约句柄。
     */
    private final Lock lock;

    /**
     * 本次抢到的 workerId。
     */
    private final int workerId;

    /**
     * 租约时长，续期按它重设有效期。
     */
    private final Duration lease;

    /**
     * 续期线程；仅在本来源存活期间存在。
     */
    private final Thread renewThread;

    /**
     * 租约是否仍然有效；续期失败后置 {@code false}，不再恢复。
     */
    private volatile boolean healthy = true;

    /**
     * 是否已关闭，保证 {@link #close()} 幂等。
     */
    private volatile boolean closed = false;

    /**
     * 以已抢到的租约构造来源；线程在此创建但尚未启动，由 {@link #open(Duration)} 启动。
     *
     * @param lock 已持有的租约句柄
     * @param workerId 本次抢到的 workerId
     * @param lease 租约时长
     */
    private LockWorkerIdSource(Lock lock, int workerId, Duration lease) {
        this.lock = lock;
        this.workerId = workerId;
        this.lease = lease;
        this.renewThread = new Thread(new Runnable() {
            @Override
            public void run() {
                renewLoop();
            }
        }, THREAD_NAME);
        this.renewThread.setDaemon(true);
    }

    /**
     * 抢占一个可用 workerId 并启动续期线程。
     *
     * @param lease 租约时长，须至少 1ms
     * @return 已持有租约的来源
     * @throws IdException workerId 全部被占用，或锁模块未装配 / 存储不可用
     */
    public static LockWorkerIdSource open(Duration lease) {
        for (int candidate = IdNames.MIN_WORKER_ID; candidate <= IdNames.MAX_WORKER_ID; candidate++) {
            Lock acquired = tryLock(candidate, lease);
            if (acquired != null) {
                LockWorkerIdSource source = new LockWorkerIdSource(acquired, candidate, lease);
                source.renewThread.start();
                return source;
            }
        }
        throw IdErrors.noWorkerIdAvailable();
    }

    /**
     * 返回本次抢到的 workerId。
     *
     * @return workerId，取值 {@code 0..1023}
     */
    @Override
    public int acquire() {
        return workerId;
    }

    /**
     * 返回租约是否仍然有效。
     *
     * @return 续期正常且未关闭时 {@code true}；租约丢失或已关闭时 {@code false}
     */
    @Override
    public boolean healthy() {
        return healthy && !closed;
    }

    /**
     * 停机收敛：中断并等待续期线程结束，然后释放租约；重复调用无副作用。
     *
     * <p>不向外抛异常：释放失败按错误记录后吞掉，避免停机路径掩盖主流程异常。</p>
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        healthy = false;
        renewThread.interrupt();
        try {
            renewThread.join(CLOSE_JOIN_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            lock.release();
        } catch (RuntimeException e) {
            log().error("aifei-id worker id lease release failed, workerId: " + workerId, e);
        }
    }

    /**
     * 续期循环：按周期调用 {@link Lock#renew()}，失败即置不可用并退出。
     */
    private void renewLoop() {
        long interval = Math.max(MIN_RENEW_INTERVAL_MILLIS, lease.toMillis() / 3L);
        while (!closed) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (closed) {
                return;
            }
            try {
                if (!lock.renew()) {
                    healthy = false;
                    log().error("aifei-id worker id lease lost, workerId: " + workerId
                            + ", stop generating ids to avoid duplicates");
                    return;
                }
            } catch (RuntimeException e) {
                healthy = false;
                log().error("aifei-id worker id lease renew failed, workerId: " + workerId, e);
                return;
            }
        }
    }

    /**
     * 尝试占用指定 workerId 的租约。
     *
     * @param workerId 候选 workerId
     * @param lease 租约时长
     * @return 抢到时返回句柄；已被其它实例持有时返回 {@code null}
     * @throws IdException 锁模块未装配或存储不可用
     */
    private static Lock tryLock(int workerId, Duration lease) {
        try {
            return LockKit.tryLock(IdNames.workerLockName(workerId), lease);
        } catch (IdException e) {
            throw e;
        } catch (RuntimeException e) {
            throw IdErrors.assemblyNotReady(DEPENDENCY, String.valueOf(e.getMessage()));
        }
    }

    /**
     * 惰性获取本类日志：不在静态字段持有，避免类初始化期命中未装配的日志门面。
     *
     * @return 本类日志
     */
    private static Log log() {
        return Log.get(LockWorkerIdSource.class);
    }
}
