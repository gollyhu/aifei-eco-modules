/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单通道配置值对象（不可变，非承诺实现层）：承载通道标识、厂商类型与原始 kebab-case 配置项。
 *
 * <p>由配置解析唯一定义点构造；验签密钥等厂商配置项经 {@link #getOption(String)} 按原键名读取。</p>
 */
public final class SmsChannelConfig {

    private final String name;
    private final String type;
    private final Map<String, String> options;

    /**
     * @param name    通道标识
     * @param type    厂商 supplier 字面量
     * @param options 原始配置项（kebab-case 键）
     */
    public SmsChannelConfig(String name, String type, Map<String, String> options) {
        this.name = name;
        this.type = type;
        this.options = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(options == null ? Collections.<String, String>emptyMap() : options));
    }

    /**
     * @return 通道标识
     */
    public String getName() {
        return name;
    }

    /**
     * @return 厂商 supplier 字面量
     */
    public String getType() {
        return type;
    }

    /**
     * @return 原始配置项（不可变）
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
