/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache;

/**
 * 缓存 key 的匹配类型，用于 {@link Cache#keys(String, int, int, String, CacheKeyMatchType)} 的筛选。
 *
 * <p>匹配一律基于 key 的字符串形态；{@code pattern} 为 {@code null} 或空串时视为不筛选，由调用方跳过匹配。</p>
 */
public enum CacheKeyMatchType {

    /**
     * 完全相等：{@code key.equals(pattern)}。
     */
    EQUALS,

    /**
     * 包含：{@code key.contains(pattern)}。
     */
    CONTAINS,

    /**
     * 前缀匹配：{@code key.startsWith(pattern)}。
     */
    STARTS_WITH,

    /**
     * 后缀匹配：{@code key.endsWith(pattern)}。
     */
    ENDS_WITH
}
