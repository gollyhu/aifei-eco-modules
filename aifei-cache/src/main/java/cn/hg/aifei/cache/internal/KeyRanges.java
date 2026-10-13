/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.hg.aifei.cache.CacheKeyMatchType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * keys 的筛选、排序与分页的唯一定义点，两后端实现共用。
 */
public final class KeyRanges {

    private KeyRanges() {
    }

    /**
     * 对键集合做「筛选 + 字典序排序 + 分页」：{@code pattern} 为 {@code null} 或空串时跳过筛选；
     * {@code offset} 是跳过的条数、{@code limit} 是返回条数上限，越界自动截断，不抛异常。
     *
     * @param keys 待处理键集合，顺序不限
     * @param offset 跳过的键条数，从 0 开始
     * @param limit 返回的键条数上限
     * @param pattern 匹配串；{@code null} 或空串表示不筛选
     * @param matchType 匹配类型
     * @return 筛选并排序后的分页结果；不为 {@code null}
     */
    public static List<String> select(List<String> keys, int offset, int limit,
                                      String pattern, CacheKeyMatchType matchType) {
        boolean filtering = pattern != null && !pattern.isEmpty();
        List<String> matched = new ArrayList<>();
        for (String key : keys) {
            if (!filtering || matches(key, pattern, matchType)) {
                matched.add(key);
            }
        }
        Collections.sort(matched);
        if (offset >= matched.size()) {
            return Collections.emptyList();
        }
        int count = Math.min(limit, matched.size() - offset);
        return new ArrayList<>(matched.subList(offset, offset + count));
    }

    /**
     * 按匹配类型判定键与匹配串是否吻合，匹配基于键的字符串形态。
     */
    private static boolean matches(String key, String pattern, CacheKeyMatchType matchType) {
        switch (matchType) {
            case EQUALS:
                return key.equals(pattern);
            case STARTS_WITH:
                return key.startsWith(pattern);
            case ENDS_WITH:
                return key.endsWith(pattern);
            case CONTAINS:
            default:
                return key.contains(pattern);
        }
    }
}
