/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.aifei.util.Prop;
import cn.hg.aifei.cache.Cache;

/**
 * 一种缓存后端的装配契约：解析本后端配置并构造实例。非承诺 API，仅供装配使用。
 */
public interface CacheProvider {

    /**
     * 后端类型标识，对应配置项 {@code cache.type} 的取值，如 {@code "jedis"} / {@code "caffeine"}。
     */
    String type();

    /**
     * 解析本实例的配置并构造缓存实例；不保存任何解析结果，可被重复调用。
     */
    Cache buildCache(String instanceName, String prefix, Prop prop);
}
