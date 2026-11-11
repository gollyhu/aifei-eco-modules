/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.internal;

import cn.hg.aifei.cache.CacheKit;
import cn.hg.aifei.cache.Counter;
import cn.hg.aifei.cache.CounterKit;
import cn.hg.aifei.id.IdException;

/**
 * 共享后端能力校验的唯一定义点：取主计数器，并确认后端支持条件操作。
 *
 * <p>号段取段与 cache 模式 workerId 都依赖「一次原子自增拿到唯一区间 / 唯一序号」，这要求后端是
 * <b>共享存储</b>（{@code Cache#supportsCas() == true}，如 Redis）。本地 Caffeine 后端不支持条件操作，
 * 多实例会各自从 1 开始、必然重号，因此启动期直接拒绝，不做降级。</p>
 *
 * <p>本类只在 {@link IdDeps#requireCache()} 探测通过后被调用；取实例失败（例如 {@code CachePlugin} 未启动
 * 或启动顺序不对）会包装成携带底层说明的 {@link IdException}。</p>
 *
 * <p>非承诺 API。</p>
 */
public final class CacheAccess {

    /**
     * 协同模块名，用于装配未就绪时的消息定位。
     */
    private static final String DEPENDENCY = "aifei-cache";

    /**
     * 禁止创建工具类实例。
     */
    private CacheAccess() {
    }

    /**
     * 取主计数器并校验后端为共享存储。
     *
     * @return 主缓存派生出的主计数器，与号段分区共用命名空间
     * @throws IdException 缓存实例不可用（装配未就绪）或后端不是共享存储
     */
    public static Counter requireSharedCounter() {
        Counter counter;
        boolean shared;
        try {
            counter = CounterKit.use();
            shared = CacheKit.use().supportsCas();
        } catch (IdException e) {
            throw e;
        } catch (RuntimeException e) {
            throw IdErrors.assemblyNotReady(DEPENDENCY, String.valueOf(e.getMessage()));
        }
        if (!shared) {
            throw IdErrors.notSharedBackend();
        }
        return counter;
    }
}
