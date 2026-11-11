/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.support;

import cn.hg.aifei.lock.internal.LockStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单测用锁存储：以「前 N 个 workerId 已被占用」为唯一可编排输入，记录调用顺序，不触达任何外部存储。
 *
 * <p>{@code aifei-lock} 的 {@code Lock} 是 {@code final} 且构造器包私有，无法直接伪造句柄；但
 * {@code LockKit} 是从 {@code LockHolder} 取存储实现的，因此用例先 {@code LockHolder.register(本桩)}
 * 再启动装配入口，即可让「0..1023 升序抢占」「全部占用」「停机释放」三条路径离线可测，无需真实 Redis
 * 或真实锁。</p>
 *
 * <p>workerId 由锁名尾段的数字得出（锁名规则本身由 {@code internal.IdNames} 负责拼装，本桩不复制规则）。</p>
 *
 * <p>线程安全：全部方法 {@code synchronized}，返回的列表是不可变快照。</p>
 */
public final class FakeLockStore implements LockStore {

    /**
     * 全占用哨兵：不存在任何可用的 workerId。
     */
    private static final int ALL_OCCUPIED = -1;

    /**
     * 首个可用的 workerId；{@link #ALL_OCCUPIED} 表示 {@code 0..1023} 全被占用。
     */
    private final int firstFreeWorkerId;

    /**
     * {@link #tryAcquire} 的锁名，按调用顺序记录。
     */
    private final List<String> attempts = new ArrayList<>();

    /**
     * {@link #release} 的锁名，按调用顺序记录。
     */
    private final List<String> released = new ArrayList<>();

    /**
     * 以「首个可用 workerId」构造存储。
     *
     * @param firstFreeWorkerId 首个可用的 workerId，{@code >= 0}
     */
    private FakeLockStore(int firstFreeWorkerId) {
        this.firstFreeWorkerId = firstFreeWorkerId;
    }

    /**
     * 构造「{@code 0..1023} 全部被其它实例占用」的存储。
     *
     * @return 永不成功的存储
     */
    public static FakeLockStore allOccupied() {
        return new FakeLockStore(ALL_OCCUPIED);
    }

    /**
     * 构造「从指定 workerId 起可用」的存储：小于它的候选一律抢不到，用于验证升序抢占。
     *
     * @param firstFreeWorkerId 首个可用的 workerId，不得为负
     * @return 存储
     * @throws IllegalArgumentException {@code firstFreeWorkerId} 为负
     */
    public static FakeLockStore freeFrom(int firstFreeWorkerId) {
        if (firstFreeWorkerId < 0) {
            throw new IllegalArgumentException("firstFreeWorkerId must not be negative");
        }
        return new FakeLockStore(firstFreeWorkerId);
    }

    /**
     * 记录一次抢占；候选 workerId 不小于首个可用值时成功。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @param lease 租约时长
     * @return 抢到时 {@code true}
     */
    @Override
    public synchronized boolean tryAcquire(String name, String token, Duration lease) {
        attempts.add(name);
        return firstFreeWorkerId >= 0 && workerIdOf(name) >= firstFreeWorkerId;
    }

    /**
     * 记录一次释放；恒成功（释放失败路径不在本轮的覆盖范围）。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @return 恒为 {@code true}
     */
    @Override
    public synchronized boolean release(String name, String token) {
        released.add(name);
        return true;
    }

    /**
     * 续期恒成功：续期失败导致「租约丢失」的路径需要真实等待续期周期，离线单测改由
     * {@link FakeWorkerIdSource#breakLease()} 覆盖。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @param lease 新的租约时长
     * @return 恒为 {@code true}
     */
    @Override
    public boolean renew(String name, String token, Duration lease) {
        return true;
    }

    /**
     * 返回抢占顺序快照。
     *
     * @return 被尝试过的锁名，按调用顺序
     */
    public synchronized List<String> attempts() {
        return Collections.unmodifiableList(new ArrayList<>(attempts));
    }

    /**
     * 返回释放顺序快照。
     *
     * @return 被释放的锁名，按调用顺序
     */
    public synchronized List<String> released() {
        return Collections.unmodifiableList(new ArrayList<>(released));
    }

    /**
     * 从锁名尾段取出 workerId，避免在桩里复制锁名拼装规则。
     *
     * @param name 锁名
     * @return workerId
     */
    private static int workerIdOf(String name) {
        return Integer.parseInt(name.substring(name.lastIndexOf('.') + 1));
    }
}
