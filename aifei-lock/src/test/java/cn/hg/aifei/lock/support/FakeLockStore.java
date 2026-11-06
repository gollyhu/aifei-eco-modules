/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.support;

import cn.hg.aifei.lock.internal.LockStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存版锁存储替身：按三条原子命令的语义模拟持有关系，并支持注入一次性故障与模拟租约到期。
 *
 * <p>读方法（{@link #tokenOf} / {@link #leaseOf} / {@link #renewLeases} / {@link #acquireCalls}）
 * 供用例断言实现是否把正确的标识与租约传给了存储层。</p>
 */
public class FakeLockStore implements LockStore {

    /**
     * 锁名到当前持有者的映射。
     */
    private final Map<String, Holder> holders = new HashMap<>();

    /**
     * {@code renew} 收到的租约历史，按调用顺序追加。
     */
    private final List<Duration> renewLeases = new ArrayList<>();

    /**
     * 下一次 {@code tryAcquire} 要抛出的异常。
     */
    private RuntimeException nextAcquireFailure;

    /**
     * 指定第几次 {@code tryAcquire} 抛出异常，1 起算。
     */
    private int scheduledAcquireCall;

    /**
     * 与 {@link #scheduledAcquireCall} 配对的异常。
     */
    private RuntimeException scheduledAcquireFailure;

    /**
     * 下一次 {@code release} 要抛出的异常。
     */
    private RuntimeException nextReleaseFailure;

    /**
     * 下一次 {@code renew} 要抛出的异常。
     */
    private RuntimeException nextRenewFailure;

    /**
     * {@code tryAcquire} 的累计调用次数。
     */
    private int acquireCalls;

    @Override
    public synchronized boolean tryAcquire(String name, String token, Duration lease) {
        acquireCalls++;
        if (scheduledAcquireFailure != null && acquireCalls == scheduledAcquireCall) {
            RuntimeException error = scheduledAcquireFailure;
            scheduledAcquireFailure = null;
            throw error;
        }
        if (nextAcquireFailure != null) {
            RuntimeException error = nextAcquireFailure;
            nextAcquireFailure = null;
            throw error;
        }
        if (holders.containsKey(name)) {
            return false;
        }
        holders.put(name, new Holder(token, lease));
        return true;
    }

    @Override
    public synchronized boolean release(String name, String token) {
        if (nextReleaseFailure != null) {
            RuntimeException error = nextReleaseFailure;
            nextReleaseFailure = null;
            throw error;
        }
        Holder holder = holders.get(name);
        if (holder == null || !holder.token.equals(token)) {
            return false;
        }
        holders.remove(name);
        return true;
    }

    @Override
    public synchronized boolean renew(String name, String token, Duration lease) {
        if (nextRenewFailure != null) {
            RuntimeException error = nextRenewFailure;
            nextRenewFailure = null;
            throw error;
        }
        renewLeases.add(lease);
        Holder holder = holders.get(name);
        if (holder == null || !holder.token.equals(token)) {
            return false;
        }
        holder.lease = lease;
        return true;
    }

    /**
     * 模拟租约到期：移除当前持有者，此后旧句柄的 {@code release} / {@code renew} 都会失配。
     *
     * @param name 锁名
     */
    public synchronized void expire(String name) {
        holders.remove(name);
    }

    /**
     * 让下一次 {@code tryAcquire} 抛异常。
     *
     * @param error 待抛出的异常
     */
    public synchronized void failNextAcquire(RuntimeException error) {
        this.nextAcquireFailure = error;
    }

    /**
     * 让第 {@code call} 次 {@code tryAcquire} 抛异常，用于验证轮询期间异常立即上抛。
     *
     * @param call 第几次调用，1 起算
     * @param error 待抛出的异常
     */
    public synchronized void failAcquireOnCall(int call, RuntimeException error) {
        this.scheduledAcquireCall = call;
        this.scheduledAcquireFailure = error;
    }

    /**
     * 让下一次 {@code release} 抛异常。
     *
     * @param error 待抛出的异常
     */
    public synchronized void failNextRelease(RuntimeException error) {
        this.nextReleaseFailure = error;
    }

    /**
     * 让下一次 {@code renew} 抛异常。
     *
     * @param error 待抛出的异常
     */
    public synchronized void failNextRenew(RuntimeException error) {
        this.nextRenewFailure = error;
    }

    /**
     * 取当前持有者标识。
     *
     * @param name 锁名
     * @return 持有者标识；无人持有时为 {@code null}
     */
    public synchronized String tokenOf(String name) {
        Holder holder = holders.get(name);
        return holder == null ? null : holder.token;
    }

    /**
     * 取当前租约。
     *
     * @param name 锁名
     * @return 当前租约；无人持有时为 {@code null}
     */
    public synchronized Duration leaseOf(String name) {
        Holder holder = holders.get(name);
        return holder == null ? null : holder.lease;
    }

    /**
     * 取 {@code renew} 收到的租约历史。
     *
     * @return 按调用顺序的租约列表
     */
    public synchronized List<Duration> renewLeases() {
        return new ArrayList<>(renewLeases);
    }

    /**
     * 取 {@code tryAcquire} 的累计调用次数。
     *
     * @return 调用次数
     */
    public synchronized int acquireCalls() {
        return acquireCalls;
    }

    /**
     * 单个锁名的持有者。
     */
    private static final class Holder {

        private final String token;

        private Duration lease;

        private Holder(String token, Duration lease) {
            this.token = token;
            this.lease = lease;
        }
    }
}
