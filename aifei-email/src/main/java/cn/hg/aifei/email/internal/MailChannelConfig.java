/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单通道配置值对象（不可变，非承诺实现层）：承载通道标识与原始 kebab-case 配置项。
 *
 * <p>由配置解析唯一定义点构造；键名到内核配置字段的映射由适配层完成，本类只做数据承载。</p>
 */
public final class MailChannelConfig {

    private final String name;
    private final Map<String, String> options;

    /**
     * @param name    通道标识
     * @param options 原始配置项（kebab-case 键名到值，已填充默认值）
     */
    public MailChannelConfig(String name, Map<String, String> options) {
        this.name = name;
        this.options = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                options == null ? Collections.<String, String>emptyMap() : options));
    }

    /**
     * @return 通道标识
     */
    public String getName() {
        return name;
    }

    /**
     * @return 原始配置项（不可变，kebab-case 键名）
     */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * 按原键名取配置项。
     *
     * @param key kebab-case 配置键（不含通道前缀）
     * @return 配置值，不存在返回 {@code null}
     */
    public String getOption(String key) {
        return options.get(key);
    }
}
