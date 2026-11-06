/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock;

import cn.aifei.log.Log;
import cn.hg.aifei.lock.internal.LockErrors;
import cn.hg.aifei.lock.internal.LockStore;
import java.time.Duration;

/**
 * 不可变锁句柄：一次持有的凭据，只能由 {@link LockKit} 创建。
 *
 * <p>全字段 final、不记录持有状态，天然线程安全，可跨线程传递；采用对象身份比较，同名两次持有互不相等。
 * 释放与续期都用句柄内绑定的标识做 CAS，不会误删他人持有的锁。</p>
 *
 * <p>句柄只对应一次持有，不能存到全局字段反复使用。</p>
 */
public final class Lock implements AutoCloseable {

    /**
     * 本类日志。
     */
    private static final Log LOG = Log.get(Lock.class);

    /**
     * 绑定的锁名。
     */
    private final String name;

    /**
     * 本次持有的标识，不对外暴露。
     */
    private final String token;

    /**
     * 创建时的租约时长，{@link #renew()} 以它重设有效期。
     */
    private final Duration lease;

    /**
     * 加锁所在的存储。
     */
    private final LockStore store;

    /**
     * 由门面构造句柄；构造过程不执行 I/O。
     *
     * @param name 锁名
     * @param token 本次持有的标识
     * @param lease 创建时的租约时长
     * @param store 存储实现
     */
    Lock(String name, String token, Duration lease, LockStore store) {
        this.name = name;
        this.token = token;
        this.lease = lease;
        this.store = store;
    }

    /**
     * 返回绑定的锁名。
     *
     * @return 锁名
     */
    public String name() {
        return name;
    }

    /**
     * CAS 释放：仅当锁仍由本句柄持有时删除。
     *
     * @return 本次释放成功时 {@code true}；非持有者（已过期或被他人持有）时 {@code false}
     * @throws LockException 存储不可用、超时或序列化失败；只表示「是否删除未知」，重试安全
     */
    public boolean release() {
        try {
            boolean released = store.release(name, token);
            LOG.debug("lock release " + (released ? "succeeded" : "skipped") + ", name: " + name);
            return released;
        } catch (RuntimeException e) {
            throw fail("release", e);
        }
    }

    /**
     * CAS 续期：仅当锁仍由本句柄持有时按创建时的租约重设有效期。
     *
     * @return 续期成功时 {@code true}；值不匹配（锁已丢失）时 {@code false}，业务须立即停手
     * @throws LockException 存储不可用、超时或序列化失败；只表示「结果未知」，不能据此断定锁已丢失
     */
    public boolean renew() {
        try {
            boolean renewed = store.renew(name, token, lease);
            LOG.debug("lock renew " + (renewed ? "succeeded" : "skipped") + ", name: " + name);
            return renewed;
        } catch (RuntimeException e) {
            throw fail("renew", e);
        }
    }

    /**
     * 等价于 {@link #release()} 并吞掉异常：永不向外抛，供 try-with-resources 使用。
     *
     * <p>释放失败按 ERROR 记录（由 {@link #release()} 完成），不向外抛以免抑制业务真实异常；
     * 需要感知释放失败时显式调用 {@link #release()}。</p>
     */
    @Override
    public void close() {
        try {
            release();
        } catch (RuntimeException ignored) {
            // release() 已按 ERROR 记录，close() 只负责不向外抛
        }
    }

    /**
     * 按包装规则构造待抛出异常并记录 ERROR。
     *
     * @param operation 操作名
     * @param cause 原始异常
     * @return 待抛出异常
     */
    private RuntimeException fail(String operation, RuntimeException cause) {
        RuntimeException error = LockErrors.wrap(operation, name, cause);
        LOG.error("lock " + operation + " failed, name: " + name, error);
        return error;
    }
}
