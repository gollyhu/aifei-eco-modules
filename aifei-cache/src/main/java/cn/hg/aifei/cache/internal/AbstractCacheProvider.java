/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.Cache;

/**
 * 后端 provider 的装配基类：装配通用配置项，再委托子类补本后端专有配置。
 *
 * <p>本类不实现 {@link CacheProvider#buildCache} —— 缓存实例由各子类在自己的 {@code buildCache}
 * 内创建。基类不持有实例字段，可被多线程复用。</p>
 */
public abstract class AbstractCacheProvider<C extends Config> implements CacheProvider {

    public abstract String type();

    @Override
    public abstract Cache buildCache(String instanceName, String prefix, Prop prop);

    /**
     * 装配通用项后委托 {@link #buildConfig}；返回的配置对象已是完整可用的实例配置。
     *
     * <p>{@code prefix} 在委托之前写入配置对象，供子类 setter 拼装配置键。</p>
     */
    protected C initConfig(C cfg, String instanceName, String prefix, Prop prop) {
        if (cfg == null) {
            throw new IllegalArgumentException("cfg can not be blank");
        }

        cfg.instanceName(instanceName)
                .prefix(prefix)
                .type(this.type())
                .namespace(Config.resolveNamespace(prop, prefix))
                .ttl(Config.resolveTtl(prop, prefix))
                .cacheNull(Config.resolveCacheNull(prop, prefix));

        return this.buildConfig(cfg, prefix, prop);
    }

    /**
     * 补齐本后端专有配置项。实现方以链式 setter 一次写完，勿手写配置键。
     */
    protected abstract C buildConfig(C cfg, String prefix, Prop prop);

}
