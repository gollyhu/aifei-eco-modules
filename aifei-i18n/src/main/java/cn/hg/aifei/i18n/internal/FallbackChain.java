/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.i18n.MessageSource;
import java.util.List;

/**
 * 回退链唯一定义点：
 * {@code 按 locale 逐来源 → 默认 locale 逐来源 → 返回 key 原文}，固定不可配、无 strict 模式。
 *
 * <p>日志分级：未命中 DEBUG（含 key + locale），来源运行期故障 WARN（含稳定名 + key）后按「本次无译文」
 * 继续回退，绝不向上抛（取文案路径不得让业务接口 500）。</p>
 */
public final class FallbackChain {

    private FallbackChain() {
    }

    /**
     * 两轮逐来源取文案。
     *
     * @param key           文案 key
     * @param localeTag     已规范化的生效 locale
     * @param sources       按叠加顺序排列的来源
     * @param defaultLocale 已规范化的默认 locale（回退链终点前一轮）
     * @param cache         本地缓存层
     * @return 命中的译文原文（可为空串）；全部未命中返回 {@code key} 原文
     */
    public static String get(String key, String localeTag, List<MessageSource> sources,
                             String defaultLocale, CacheLayer cache) {
        String hit = lookup(key, localeTag, sources, cache);
        if (hit != null) {
            return hit;
        }
        if (!localeTag.equals(defaultLocale)) {
            hit = lookup(key, defaultLocale, sources, cache);
            if (hit != null) {
                return hit;
            }
        }
        debug(key, localeTag);
        return key;
    }

    /**
     * 单轮：把「按来源顺序遍历」整体作为一次缓存加载（缓存键 locale+key 对整轮生效，
     * 先命中先返回），空串是命中、{@code null} 是未命中；来源故障 WARN 后继续下一个来源。
     */
    private static String lookup(final String key, final String localeTag,
                                 final List<MessageSource> sources, CacheLayer cache) {
        return cache.get(key, localeTag, new CacheLayer.Loader() {
            @Override
            public String load() {
                for (MessageSource source : sources) {
                    try {
                        String value = source.get(key, localeTag);
                        if (value != null) {
                            return value;
                        }
                    } catch (RuntimeException e) {
                        warn(source.name(), key, e);
                    }
                }
                return null;
            }
        });
    }

    private static void debug(String key, String localeTag) {
        try {
            Log log = Log.get(FallbackChain.class);
            log.debug("i18n 文案未命中，返回 key 原文: key=" + key + ", locale=" + localeTag);
        } catch (Throwable ignored) {
            // 日志通道不可用不影响取文案
        }
    }

    private static void warn(String sourceName, String key, RuntimeException cause) {
        try {
            Log log = Log.get(FallbackChain.class);
            log.warn("i18n 来源 " + sourceName + " 取文案失败，已按未命中继续回退: key=" + key + ", 原因=" + cause);
        } catch (Throwable ignored) {
            // 日志通道不可用不影响回退
        }
    }
}
