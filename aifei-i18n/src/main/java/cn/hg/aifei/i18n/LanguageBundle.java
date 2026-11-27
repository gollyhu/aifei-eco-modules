/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 语言包的不可变值对象：一次整包导出的四字段契约（{@code locale} / {@code localeName} /
 * {@code dir} / {@code messages}）。
 *
 * <p>模块自身不序列化 JSON：消费方 Just Service 把四个访问器拼成 JSON。</p>
 */
public final class LanguageBundle {

    private final String locale;
    private final String localeName;
    private final String dir;
    private final Map<String, String> messages;

    private LanguageBundle(String locale, String localeName, String dir, Map<String, String> messages) {
        this.locale = locale;
        this.localeName = localeName;
        this.dir = dir;
        this.messages = messages;
    }

    /**
     * 唯一构造入口（模块装配用）。{@code messages} 深拷贝为不可变有序 Map，插入序由
     * {@code internal.BundleExporter} 的 {@code TreeMap} 决定，迭代顺序即 {@code String.compareTo} 升序。
     *
     * @param locale     规范化后的 locale tag
     * @param localeName JDK 给出的「该语言在该语言中的名称」
     * @param dir        {@code ltr} / {@code rtl}
     * @param messages   key 到原文（含 {@code {0}} 占位符）的映射
     * @return 不可变语言包
     * @throws IllegalArgumentException 任一参数为 {@code null}
     */
    public static LanguageBundle of(String locale, String localeName, String dir,
                                    Map<String, String> messages) {
        if (locale == null) {
            throw new IllegalArgumentException("locale 不能为 null");
        }
        if (localeName == null) {
            throw new IllegalArgumentException("localeName 不能为 null");
        }
        if (dir == null) {
            throw new IllegalArgumentException("dir 不能为 null");
        }
        if (messages == null) {
            throw new IllegalArgumentException("messages 不能为 null");
        }
        Map<String, String> copy = Collections.unmodifiableMap(new LinkedHashMap<String, String>(messages));
        return new LanguageBundle(locale, localeName, dir, copy);
    }

    /**
     * @return 规范化后的 locale tag
     */
    public String locale() {
        return locale;
    }

    /**
     * @return 该语言在该语言中的名称（JDK 内置能力，非译名表）
     */
    public String localeName() {
        return localeName;
    }

    /**
     * @return {@code ltr} 或 {@code rtl}
     */
    public String dir() {
        return dir;
    }

    /**
     * @return 不可变有序映射，value 恒为原文字符串
     */
    public Map<String, String> messages() {
        return messages;
    }
}
