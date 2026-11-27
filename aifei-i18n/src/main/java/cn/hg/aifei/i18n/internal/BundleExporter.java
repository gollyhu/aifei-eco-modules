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
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 整包导出的唯一定义点。
 *
 * <p>按叠加顺序合并可列举来源（先命中先返回），{@link TreeMap} 自然序保证 key 稳定排序；
 * value 恒为原文（{@code {0}} 不替换）；不可列举 / 故障来源跳过 + WARN；四字段由
 * {@link LocaleMeta} 与 {@link LocaleNormalizer} 装配。</p>
 */
public final class BundleExporter {

    private BundleExporter() {
    }

    /**
     * 导出指定 locale 的整包（显式 locale，不受上下文影响）。
     *
     * @param localeTag BCP 47 locale（可下划线写法，先规范化；非法 → IAE）
     * @param sources   按叠加顺序排列的来源
     * @return 不可变语言包
     * @throws IllegalArgumentException localeTag 非法
     */
    public static LanguageBundle export(String localeTag, List<MessageSource> sources) {
        String locale = LocaleNormalizer.normalize(localeTag);
        Locale jdkLocale = Locale.forLanguageTag(locale);

        TreeMap<String, String> messages = new TreeMap<String, String>();
        for (MessageSource source : sources) {
            Map<String, String> listed;
            try {
                listed = source.list(locale);
            } catch (RuntimeException e) {
                warn("i18n 来源 " + source.name() + " 整包列举失败，已跳过: " + e);
                continue;
            }
            if (listed == null) {
                warn("i18n 来源 " + source.name() + " 不支持整包列举，已跳过");
                continue;
            }
            for (Map.Entry<String, String> entry : listed.entrySet()) {
                if (!messages.containsKey(entry.getKey())) {
                    messages.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return LanguageBundle.of(locale, LocaleMeta.localeName(jdkLocale), LocaleMeta.dir(jdkLocale), messages);
    }

    private static void warn(String message) {
        try {
            Log log = Log.get(BundleExporter.class);
            log.warn(message);
        } catch (Throwable ignored) {
            // 日志通道不可用不影响导出
        }
    }
}
