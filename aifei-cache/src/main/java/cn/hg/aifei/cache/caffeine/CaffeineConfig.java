/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.caffeine;

import cn.hg.aifei.cache.internal.Config;

/**
 * caffeine 后端的实例级配置。
 *
 * <p>取值校验的归属见 {@link Config}：可选数值项遇 {@code null} 表示未配置，不赋值、保持字段缺省。
 * {@code maxSize} 的取值下限由 {@code CaffeineCache} 的构造函数校验。</p>
 */
public class CaffeineConfig extends Config {

    /**
     * {@code caffeine.maxSize} 的缺省值。
     */
    public static final long DEFAULT_MAX_SIZE = 10_000L;

    /**
     * 最大条目数。
     */
    private long maxSize = DEFAULT_MAX_SIZE;

    /**
     * 返回最大条目数。
     */
    public long maxSize() {
        return maxSize;
    }

    /**
     * 设置最大条目数；{@code null} 表示未配置，保持缺省值 {@link #DEFAULT_MAX_SIZE}。
     */
    public CaffeineConfig maxSize(Long maxSize) {
        if (maxSize != null) {
            this.maxSize = maxSize;
        }
        return this;
    }
}
