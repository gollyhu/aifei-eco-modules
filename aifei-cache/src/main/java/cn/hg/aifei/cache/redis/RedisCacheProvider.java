/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.redis;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.Cache;
import cn.hg.aifei.cache.internal.AbstractCacheProvider;
import cn.hg.aifei.cache.internal.Config;

/**
 * jedis 后端的装配 provider：解析 {@code cache.jedis.*}（扩展实例为 {@code cache.<name>.jedis.*}）
 * 连接配置并构造 {@link RedisCache}。
 *
 * <p>配置项读取只做语法解析（{@link Config#optionalText} 等），取值校验由 {@link RedisConfig}
 * 的 setter 承担；跨字段的连接池上下限关系由 {@link RedisConfig#requirePoolBounds()} 在链尾校验。
 * 未配置的项不覆盖 {@link RedisConfig} 的字段默认值，故主机与端口无需在此补缺省。</p>
 *
 * <p>键名一律经 {@link RedisConfig#jedisKey(String, String)} 拼装，不在本类重复书写 {@code jedis}
 * 分段。</p>
 */
public class RedisCacheProvider extends AbstractCacheProvider<RedisConfig> {

    /**
     * 配置项 {@code cache.type} 的取值。刻意沿用 {@code "jedis"} 以兼容既有配置文件。
     */
    private static final String TYPE = "jedis";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Cache buildCache(String instanceName, String prefix, Prop prop) {
        RedisConfig cfg = this.initConfig(new RedisConfig(), instanceName, prefix, prop);

        FuryRedisValueCodec.allowPackages(cfg.allowedPackages());

        return new RedisCache(cfg, cfg.namespace(), cfg.ttl(), cfg.cacheNull());
    }

    @Override
    protected RedisConfig buildConfig(RedisConfig cfg, String prefix, Prop prop) {
        return cfg.uri(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".uri")))
                .host(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".host")))
                .port(Config.optionalInt(prop, RedisConfig.jedisKey(prefix, ".port")))
                .user(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".user")))
                .password(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".password")))
                .database(Config.optionalInt(prop, RedisConfig.jedisKey(prefix, ".database")))
                .clientName(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".clientName")))
                .ssl(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".ssl")))
                .resp3(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".resp3")))
                .timeoutMillis(Config.optionalInt(prop, RedisConfig.jedisKey(prefix, ".timeoutMillis")))
                .connectionTimeoutMillis(Config.optionalInt(prop,
                        RedisConfig.jedisKey(prefix, ".connectionTimeoutMillis")))
                .socketTimeoutMillis(Config.optionalInt(prop,
                        RedisConfig.jedisKey(prefix, ".socketTimeoutMillis")))
                .blockingSocketTimeoutMillis(Config.optionalInt(prop,
                        RedisConfig.jedisKey(prefix, ".blockingSocketTimeoutMillis")))
                .maxTotal(Config.optionalInt(prop, RedisConfig.jedisKey(prefix, ".maxTotal")))
                .maxIdle(Config.optionalInt(prop, RedisConfig.jedisKey(prefix, ".maxIdle")))
                .minIdle(Config.optionalInt(prop, RedisConfig.jedisKey(prefix, ".minIdle")))
                .maxWaitMillis(Config.optionalLong(prop, RedisConfig.jedisKey(prefix, ".maxWaitMillis")))
                .blockWhenExhausted(Config.optionalBoolean(prop,
                        RedisConfig.jedisKey(prefix, ".blockWhenExhausted")))
                .lifo(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".lifo")))
                .fairness(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".fairness")))
                .testOnCreate(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".testOnCreate")))
                .testOnBorrow(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".testOnBorrow")))
                .testOnReturn(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".testOnReturn")))
                .testWhileIdle(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".testWhileIdle")))
                .timeBetweenEvictionRunsMillis(Config.optionalLong(prop,
                        RedisConfig.jedisKey(prefix, ".timeBetweenEvictionRunsMillis")))
                .minEvictableIdleTimeMillis(Config.optionalLong(prop,
                        RedisConfig.jedisKey(prefix, ".minEvictableIdleTimeMillis")))
                .softMinEvictableIdleTimeMillis(Config.optionalLong(prop,
                        RedisConfig.jedisKey(prefix, ".softMinEvictableIdleTimeMillis")))
                .numTestsPerEvictionRun(Config.optionalInt(prop,
                        RedisConfig.jedisKey(prefix, ".numTestsPerEvictionRun")))
                .jmxEnabled(Config.optionalBoolean(prop, RedisConfig.jedisKey(prefix, ".jmxEnabled")))
                .jmxNamePrefix(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".jmxNamePrefix")))
                .jmxNameBase(Config.optionalText(prop, RedisConfig.jedisKey(prefix, ".jmxNameBase")))
                .allowedPackages(Config.resolveAllowedPackages(prop))
                .requirePoolBounds();
    }
}
