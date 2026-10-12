/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.Counter;

/**
 * 由缓存实现提供同源计数器的内部装配接口。
 */
public interface CounterFactory {

    /**
     * 创建与当前缓存实现和配置对应的计数器。
     */
    Counter createCounter();
}
