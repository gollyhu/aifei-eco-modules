/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.lock.aifei;

import cn.aifei.plugin.Plugin;
import cn.hg.aifei.lock.cache.CacheLockStore;
import cn.hg.aifei.lock.internal.LockHolder;

/**
 * 把分布式锁装配进 aifei：应用启动时校验主缓存能力并注册运行期存储，应用停止时注销。
 *
 * <p>使用方式：在 {@code config(Plugins plugins)} 里 {@code plugins.add(new LockPlugin())}，且必须
 * 晚于 {@code CachePlugin}。本插件无配置项、不新建连接，锁键落在主缓存实例的 {@code __LOCK__} 分区。</p>
 */
public class LockPlugin implements Plugin {

    /**
     * 是否已完成装配；{@code start()} 与 {@code stop()} 据此保持幂等。
     */
    private volatile boolean started = false;

    /**
     * 校验主缓存能力并注册运行期存储；重复启动不会重复注册，校验失败不留半装配状态。
     *
     * @throws IllegalStateException 主缓存实例未初始化、能力查询失败或后端不是共享存储
     */
    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        LockHolder.register(CacheLockStore.open());
        started = true;
    }

    /**
     * 注销运行期存储；重复停止不会重复注销。
     */
    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        LockHolder.clear();
        started = false;
    }
}
