/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.caffeine;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.internal.AbstractCacheProvider;
import cn.hg.aifei.cache.internal.Config;

/**
 * Caffeine 后端的装配 provider：解析配置并构造 {@link CaffeineCache}。
 *
 * <p>配置项读取只做语法解析（{@link Config#optionalLong} 等），未配置的项不覆盖
 * {@link CaffeineConfig} 的字段缺省值。</p>
 */
public class CaffeineCacheProvider extends AbstractCacheProvider<CaffeineConfig> {

    /**
     * 配置项 {@code cache.type} 的取值。
     */
    private static final String TYPE = "caffeine";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Cache buildCache(String instanceName, String prefix, Prop prop) {
        CaffeineConfig cfg = this.initConfig(new CaffeineConfig(), instanceName, prefix, prop);

        return new CaffeineCache(cfg.maxSize(), cfg.namespace(), cfg.ttl(), cfg.cacheNull());
    }

    @Override
    protected CaffeineConfig buildConfig(CaffeineConfig cfg, String prefix, Prop prop) {
        return cfg.maxSize(Config.optionalLong(prop, prefix + ".caffeine.maxSize"));
    }
}
