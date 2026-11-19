/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.defaultSource;

import cn.hg.aifei.i18n.MessageSource;
import java.util.Map;

/**
 * 默认来源·kv：经 {@code aifei-cache}（optional）的 {@code CacheKit} 按 key 命中。
 *
 * <p>不可列举（D-6）：{@link #list(String)} 返回 {@code null}，整包导出时跳过 + WARN。</p>
 *
 * <p><b>类加载期依赖防护</b>（AC-11-1）：字段、方法签名、父类只出现 JDK / {@link MessageSource} 类型；
 * 对 {@code cn.hg.aifei.cache.CacheKit} 的引用只在方法体内用全限定名（JVM 延迟解析），使本类在
 * classpath 无 aifei-cache 时仍可加载；由 {@code SourceRegistry} 在实例化前用 {@code Class.forName} 探测，
 * 缺失即启动失败，不静默退回 properties。</p>
 */
public final class KvSource implements MessageSource {

    /** 缓存名（namespace），避免与消费方其它缓存键冲突。 */
    private static final String CACHE_NAME = "aifei_i18n_message";
    /** locale 与 key 在缓存键中的分隔符。 */
    private static final char KEY_SEPARATOR = '\u0001';

    @Override
    public String name() {
        return "kv";
    }

    @Override
    public String get(String key, String localeTag) {
        String cacheKey = localeTag + KEY_SEPARATOR + key;
        Object value = cn.hg.aifei.cache.CacheKit.get(CACHE_NAME, cacheKey);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * kv 来源不支持整包列举：返回 {@code null}，整包导出时跳过 + WARN（Q2 / AC-09-6）。
     *
     * @param localeTag 已规范化的 locale tag
     * @return {@code null}
     */
    @Override
    public Map<String, String> list(String localeTag) {
        return null;
    }
}
