/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import java.util.Properties;
import java.util.Set;

/**
 * 配置域与键常量的唯一定义点：值对象持有后端类型与键前缀，静态入口完成 {@code mq.type} 的解析与校验。
 *
 * <p>配置对象与全部键常量、键拼装同在一处，实现类不得手写 {@code "mq.memory."} 之类的字面量。</p>
 */
public final class MqConfig {

    /**
     * 根级配置键前缀：全部配置都在它下面。
     */
    public static final String ROOT_PREFIX = "mq";

    /**
     * 后端类型配置键。
     */
    public static final String TYPE_KEY = ROOT_PREFIX + ".type";

    /**
     * 未显式配置后端类型时使用的默认类型，也是后端标识字面量的唯一来源。
     */
    public static final String DEFAULT_TYPE = "memory";

    /**
     * 后端类型。
     */
    private final String type;

    /**
     * 本后端的配置键前缀，形如 {@code mq.<type>}。
     */
    private final String prefix;

    /**
     * 全部配置项；后端专有键经 {@link #get(String)} 读取。
     */
    private final Properties properties;

    /**
     * 禁止外部构造，只能经 {@link #parse(Properties, Set)} 创建。
     */
    private MqConfig(String type, String prefix, Properties properties) {
        this.type = type;
        this.prefix = prefix;
        this.properties = properties;
    }

    /**
     * 解析并校验配置：读 {@code mq.type}，缺省取 {@link #DEFAULT_TYPE}，取值必须落在已注册类型内。
     *
     * @param properties 全部配置项
     * @param registeredTypes 已注册的后端类型
     * @return 配置对象
     * @throws IllegalArgumentException {@code properties} 或 {@code registeredTypes} 为 {@code null}，
     *         或 {@code mq.type} 指向未注册的后端
     */
    public static MqConfig parse(Properties properties, Set<String> registeredTypes) {
        if (properties == null) {
            throw new IllegalArgumentException("properties can not be null");
        }
        if (registeredTypes == null) {
            throw new IllegalArgumentException("registeredTypes can not be null");
        }
        String raw = properties.getProperty(TYPE_KEY);
        String type = (raw == null || raw.trim().isEmpty()) ? DEFAULT_TYPE : raw.trim();
        if (!registeredTypes.contains(type)) {
            throw new IllegalArgumentException(
                    "unsupported mq type: " + type + ", supported types: " + registeredTypes
            );
        }
        return new MqConfig(type, ROOT_PREFIX + "." + type, properties);
    }

    /**
     * 返回后端类型。
     *
     * @return 后端类型
     */
    public String type() {
        return type;
    }

    /**
     * 返回本后端的配置键前缀。
     *
     * @return 形如 {@code mq.<type>} 的前缀
     */
    public String prefix() {
        return prefix;
    }

    /**
     * 读取本后端的某项配置；键名相对本后端，无需带前缀。
     *
     * @param relativeKey 相对键名
     * @return 配置值；未配置时 {@code null}
     */
    public String get(String relativeKey) {
        return properties.getProperty(prefix + "." + relativeKey);
    }
}
