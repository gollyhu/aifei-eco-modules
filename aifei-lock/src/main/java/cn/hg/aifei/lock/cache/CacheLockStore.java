/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.cache;

import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.CacheKit;
import cn.hg.aifei.lock.internal.LockErrors;
import cn.hg.aifei.lock.internal.LockNames;
import cn.hg.aifei.lock.internal.LockStore;
import java.time.Duration;

/**
 * 基于共享缓存的锁存储实现：加锁走单条 {@code SET NX PX}，释放与续期走按字节比较的 CAS。
 *
 * <p>非承诺 API。需显式引入 {@code aifei-cache >= 1.1.0}，否则本类在类加载期即
 * {@code NoClassDefFoundError}（早于 {@code LockPlugin} 启动校验）。</p>
 */
public final class CacheLockStore implements LockStore {

    /**
     * 锁所在的主缓存实例。
     */
    private final Cache cache;

    /**
     * 使用已通过能力校验的主缓存实例创建存储。
     *
     * @param cache 主缓存实例
     */
    private CacheLockStore(Cache cache) {
        this.cache = cache;
    }

    /**
     * 取主缓存实例并校验其支持条件操作；启动期失败一律抛 {@link IllegalStateException}。
     *
     * <p>能力查询是无 I/O 的纯声明，因此本方法不探活、不建连：存储故障推迟到首次加锁，由运行期的
     * {@code LockException} 承载。</p>
     *
     * @return 锁存储实现
     * @throws IllegalStateException 主缓存实例取不到、能力查询自身抛异常，或后端不是共享存储
     */
    public static CacheLockStore open() {
        Cache cache;
        try {
            cache = CacheKit.use();
        } catch (RuntimeException e) {
            throw LockErrors.mainInstanceNotInitialized(e);
        }

        boolean supportsCas;
        try {
            supportsCas = cache.supportsCas();
        } catch (RuntimeException e) {
            throw LockErrors.capabilityCheckFailed(e);
        }

        if (!supportsCas) {
            throw LockErrors.notSharedStore();
        }
        return new CacheLockStore(cache);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryAcquire(String name, String token, Duration lease) {
        return cache.putIfAbsent(LockNames.CACHE_NAME, name, token, lease);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean release(String name, String token) {
        return cache.removeIfEquals(LockNames.CACHE_NAME, name, token);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean renew(String name, String token, Duration lease) {
        return cache.expireIfEquals(LockNames.CACHE_NAME, name, token, lease);
    }
}
