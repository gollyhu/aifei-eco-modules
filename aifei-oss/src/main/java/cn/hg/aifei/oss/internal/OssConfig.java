/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个存储平台的配置值对象，并是<b>字段级配置校验的唯一定义点</b>。
 *
 * <p>持有平台标识 {@code platform}、平台类型 {@code type} 与平台配置项 {@code options}。构造时校验
 * {@code platform} 与 {@code type} 非空白；其它实现类不得复制该校验。{@code options} 以不可修改副本保存。</p>
 *
 * <p>{@link #toString()} 有意<b>不</b>输出 {@code options}——其中可能含 {@code secret-key} 等敏感项。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssConfig {

    /**
     * 平台标识，即 {@code oss.<name>.*} 中的 {@code <name>}。
     */
    private final String platform;

    /**
     * 平台类型，如 {@code local} / {@code aliyun-oss}。
     */
    private final String type;

    /**
     * 平台配置项（键值对，键为 kebab-case），不可修改。
     */
    private final Map<String, String> options;

    /**
     * 创建一个平台配置；在此校验 {@code platform} 与 {@code type}。
     *
     * @param platform 平台标识
     * @param type 平台类型
     * @param options 平台配置项，可为 {@code null}（视为空）
     * @throws cn.hg.aifei.oss.OssException {@code platform} 或 {@code type} 为空白
     */
    public OssConfig(String platform, String type, Map<String, String> options) {
        this.platform = requirePlatform(platform);
        this.type = requireText(type, "type");
        this.options = (options == null || options.isEmpty())
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    /**
     * 校验平台标识非空白；<b>平台标识校验的唯一定义点</b>，装配与取用路径一律调用此处，不得复制。
     *
     * @param platform 平台标识
     * @return 原平台标识
     * @throws cn.hg.aifei.oss.OssException 平台标识为 {@code null} 或全为空白
     */
    public static String requirePlatform(String platform) {
        if (platform == null || platform.trim().isEmpty()) {
            throw new cn.hg.aifei.oss.OssException("oss platform can not be blank");
        }
        return platform;
    }

    /**
     * 校验文本非空白。
     *
     * @param value 待校验文本
     * @param field 字段名，用于异常消息
     * @return 原文本
     * @throws cn.hg.aifei.oss.OssException 文本为 {@code null} 或全为空白
     */
    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new cn.hg.aifei.oss.OssException("oss platform config " + field + " can not be blank");
        }
        return value;
    }

    /**
     * 返回平台标识。
     *
     * @return 平台标识
     */
    public String platform() {
        return platform;
    }

    /**
     * 返回平台类型。
     *
     * @return 平台类型
     */
    public String type() {
        return type;
    }

    /**
     * 返回平台配置项。
     *
     * @return 不可修改的配置项 Map；无配置时为空 Map
     */
    public Map<String, String> options() {
        return options;
    }

    /**
     * 返回平台标识与类型的可读描述；有意不含配置项，避免泄露敏感项。
     *
     * @return 描述字符串
     */
    @Override
    public String toString() {
        return "OssConfig{platform=" + platform + ", type=" + type + '}';
    }
}
