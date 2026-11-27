/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.internal;

import cn.hg.aifei.i18n.I18nException;
import cn.hg.aifei.i18n.MessageSource;
import cn.hg.aifei.i18n.defaultSource.DbSource;
import cn.hg.aifei.i18n.defaultSource.KvSource;
import cn.hg.aifei.i18n.defaultSource.PropertiesSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 稳定名注册与重名判定的唯一定义点。
 *
 * <p>按 {@code i18n.sources} 声明顺序把稳定名解析为实例；三个默认来源按名惰性构造；
 * 未注册名 / 重名 / 已声明来源依赖缺失 → {@link I18nException}（消息含稳定名）；
 * 未声明的来源一律不实例化、不探测。</p>
 */
public final class SourceRegistry {

    private static final String NAME_PROPERTIES = "properties";
    private static final String NAME_DB = "db";
    private static final String NAME_KV = "kv";
    private static final String CACHE_FQN = "cn.hg.aifei.cache.CacheKit";

    private final List<MessageSource> sources;

    private SourceRegistry(List<MessageSource> sources) {
        this.sources = sources;
    }

    /**
     * 构建注册表：收集「三个默认来源 + 显式注册来源」，检测重名，按配置顺序解析为实例列表。
     *
     * @param config          已解析配置
     * @param explicitSources 消费方显式注册的来源（可为 {@code null}）
     * @return 注册表
     * @throws I18nException 重名 / 未注册 / 已声明来源依赖缺失 / 来源无法实例化
     */
    public static SourceRegistry build(I18nConfig config, List<MessageSource> explicitSources) {
        Map<String, MessageSource> byName = new HashMap<String, MessageSource>();
        byName.put(NAME_PROPERTIES, new PropertiesSource());
        byName.put(NAME_DB, new DbSource());
        // kv 为保留默认来源，但依赖 optional：此处先以 null 占位登记稳定名，
        // 仅在 i18n.sources 声明它时才做依赖探测并实例化（未声明不探测）。
        byName.put(NAME_KV, null);

        if (explicitSources != null) {
            for (MessageSource source : explicitSources) {
                String name = source.name();
                if (byName.containsKey(name)) {
                    throw new I18nException("来源稳定名重复: " + name);
                }
                byName.put(name, source);
            }
        }

        List<MessageSource> ordered = new ArrayList<MessageSource>();
        for (String name : config.sources()) {
            if (!byName.containsKey(name)) {
                throw new I18nException("来源未注册: " + name);
            }
            MessageSource source;
            if (NAME_KV.equals(name)) {
                ensureCacheDependency();
                source = new KvSource();
            } else {
                source = byName.get(name);
            }
            ordered.add(source);
        }
        return new SourceRegistry(Collections.unmodifiableList(ordered));
    }

    /**
     * @return 按叠加顺序排列的来源（只读）
     */
    public List<MessageSource> orderedSources() {
        return sources;
    }

    private static void ensureCacheDependency() {
        try {
            Class.forName(CACHE_FQN, false, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new I18nException("来源 kv 依赖缺失: 缺少 aifei-cache（请显式引入 io.github.gollyhu:aifei-cache）", e);
        }
    }
}
