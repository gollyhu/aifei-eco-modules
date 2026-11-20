/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.aifei.log.Log;
import cn.hg.aifei.i18n.LanguageBundle;
import cn.hg.aifei.i18n.MessageSource;
import java.util.List;

/**
 * 门面后端编排器：持有 config / sources / cache，把取文案 / 导出 / 刷新转发给规则类，
 * 自身不实现任何规则（设计 §6.2 / D-1）。
 */
public final class I18nEngine {

    private final I18nConfig config;
    private final List<MessageSource> sources;
    private final CacheLayer cache;

    /**
     * @param config  已解析配置
     * @param sources 按叠加顺序排列的来源
     */
    public I18nEngine(I18nConfig config, List<MessageSource> sources) {
        this.config = config;
        this.sources = sources;
        this.cache = new CacheLayer(config.cacheTtlMillis());
    }

    /**
     * @return 配置（只读）
     */
    public I18nConfig config() {
        return config;
    }

    /**
     * @return 按叠加顺序排列的来源（只读）
     */
    public List<MessageSource> sources() {
        return sources;
    }

    /**
     * 取文案：解析生效 locale → 回退链 → 命中后插值。
     *
     * @param key       文案 key
     * @param localeTag 显式 locale（可为 {@code null}）
     * @param args      插值参数
     * @return 译文或 key 原文
     */
    public String message(String key, String localeTag, Object... args) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key 不能为空");
        }
        String locale = LocaleResolver.resolve(localeTag);
        String raw = FallbackChain.get(key, locale, sources, config.defaultLocale(), cache);
        return MessageInterpolator.format(raw, args);
    }

    /**
     * 整包导出（显式 locale，不受上下文影响）。
     *
     * @param localeTag 显式 locale
     * @return 不可变语言包
     */
    public LanguageBundle bundle(String localeTag) {
        return BundleExporter.export(localeTag, sources);
    }

    /**
     * 显式全清缓存并逐个触发来源刷新；单个来源故障 WARN 后继续其它来源；幂等。
     */
    public void refresh() {
        cache.clearAll();
        for (MessageSource source : sources) {
            try {
                source.refresh();
            } catch (RuntimeException e) {
                warn(source.name(), e);
            }
        }
    }

    private static void warn(String sourceName, RuntimeException cause) {
        try {
            Log log = Log.get(I18nEngine.class);
            log.warn("i18n 来源 " + sourceName + " 刷新失败，已跳过该来源: 原因=" + cause);
        } catch (Throwable ignored) {
            // 日志通道不可用不影响刷新
        }
    }
}
