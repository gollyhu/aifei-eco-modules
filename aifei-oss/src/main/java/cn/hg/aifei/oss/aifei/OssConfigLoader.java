/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.aifei;

import cn.aifei.util.Prop;
import cn.hg.aifei.oss.OssException;
import cn.hg.aifei.oss.internal.OssConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code oss.*} 配置键名与解析的<b>唯一定义点</b>。
 *
 * <p>负责把配置源 {@link Prop} 翻译为「默认平台标识 + 各平台配置」，即：</p>
 *
 * <ol>
 *   <li>枚举形如 {@code oss.<name>.type} 的键推导平台列表（无需额外声明 {@code oss.names}）；</li>
 *   <li>为每个平台收集其下全部 {@code oss.<name>.<key>} 配置项，原样（kebab-case）交给
 *       {@link OssConfig}，<b>不</b>复制字段级校验；</li>
 *   <li>解析 {@code oss.default-platform}，执行缺省与必填规则。</li>
 * </ol>
 *
 * <p><b>默认平台规则</b>：只声明一个平台时，缺省即为该平台；声明两个及以上平台时
 * {@code oss.default-platform} 必填，缺失或指向未声明的平台都视为配置错误，抛 {@link OssException}
 * 使应用启动失败。</p>
 *
 * <p>本类属非承诺 API：位于接入层，供装配入口使用，消费方不得引用。</p>
 */
public final class OssConfigLoader {

    /**
     * 配置前缀，与既有 {@code cache.*} / {@code websocket.*} 对齐。
     */
    private static final String PREFIX = "oss.";

    /**
     * 平台类型键后缀，完整形式为 {@code oss.<name>.type}。
     */
    private static final String TYPE_SUFFIX = ".type";

    /**
     * 默认平台键。
     */
    private static final String DEFAULT_PLATFORM_KEY = "oss.default-platform";

    /**
     * 平台类型键去掉前后缀后的键名，用于收集平台配置项时排除自身。
     */
    private static final String TYPE_KEY = "type";

    /**
     * 禁止创建工具类实例。
     */
    private OssConfigLoader() {
    }

    /**
     * 载入全部平台配置。
     *
     * <p>平台列表由 {@code oss.<name>.type} 推导；每个平台的配置项以 kebab-case 原样保留，交由
     * {@link OssConfig} 校验 {@code platform} 与 {@code type}。</p>
     *
     * @param prop 配置源
     * @return 全部平台配置（只读），顺序为平台标识字典序
     * @throws OssException {@code prop} 为 {@code null}，未声明任何平台，或某平台缺少/含空白类型
     */
    public static List<OssConfig> load(Prop prop) {
        requireProp(prop);
        Properties properties = prop.getProperties();

        List<OssConfig> configs = new ArrayList<>();
        for (String platform : platformNames(properties)) {
            configs.add(new OssConfig(
                    platform,
                    properties.getProperty(PREFIX + platform + TYPE_SUFFIX),
                    optionsOf(properties, platform)));
        }
        if (configs.isEmpty()) {
            throw new OssException("no oss platform configured, declare at least one '" + PREFIX + "<name>"
                    + TYPE_SUFFIX + "'");
        }
        return Collections.unmodifiableList(configs);
    }

    /**
     * 解析默认平台标识，并执行缺省与必填规则。
     *
     * <p>只声明一个平台时返回该平台；声明两个及以上平台时要求 {@code oss.default-platform} 显式声明，
     * 且必须指向已声明的平台。</p>
     *
     * @param prop 配置源
     * @param configs 已载入的平台配置
     * @return 默认平台标识
     * @throws OssException {@code prop} 为 {@code null}，{@code configs} 为空，多平台时缺少
     *         {@code oss.default-platform}，或该键指向未声明的平台
     */
    public static String defaultPlatform(Prop prop, List<OssConfig> configs) {
        requireProp(prop);
        if (configs == null || configs.isEmpty()) {
            throw new OssException("no oss platform configured, declare at least one '" + PREFIX + "<name>"
                    + TYPE_SUFFIX + "'");
        }

        String declared = prop.get(DEFAULT_PLATFORM_KEY);
        if (declared == null) {
            if (configs.size() == 1) {
                return configs.get(0).platform();
            }
            throw new OssException(DEFAULT_PLATFORM_KEY + " is required when multiple platforms are configured,"
                    + " configured platforms: " + platformNamesOf(configs));
        }
        for (OssConfig config : configs) {
            if (config.platform().equals(declared)) {
                return declared;
            }
        }
        throw new OssException(DEFAULT_PLATFORM_KEY + " refers to an undeclared platform: " + declared
                + ", configured platforms: " + platformNamesOf(configs));
    }

    /**
     * 由配置键推导平台标识列表。
     *
     * <p>识别形式 {@code oss.<name>.type} 的键；{@code oss.default-platform} 不含 {@code .type} 后缀，
     * 因此不会被误判为平台。结果按字典序排序，保证装配与异常消息稳定。</p>
     *
     * @param properties 配置项集合
     * @return 平台标识列表
     */
    private static List<String> platformNames(Properties properties) {
        Set<String> names = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(TYPE_SUFFIX)) {
                continue;
            }
            String name = key.substring(PREFIX.length(), key.length() - TYPE_SUFFIX.length());
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * 收集某个平台下的全部配置项。
     *
     * <p>只接受形如 {@code oss.<name>.<key>} 且 {@code <key>} 不含 {@code .} 的键，因此：类型键
     * {@code oss.<name>.type} 被排除；当平台标识之间构成前缀关系（如 {@code a} 与 {@code a.b}）时，
     * 归属不会互相串味。</p>
     *
     * @param properties 配置项集合
     * @param platform 平台标识
     * @return 该平台的配置项（kebab-case 键，保留原始取值）
     */
    private static Map<String, String> optionsOf(Properties properties, String platform) {
        String prefix = PREFIX + platform + ".";
        Map<String, String> options = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String option = key.substring(prefix.length());
            if (option.isEmpty() || TYPE_KEY.equals(option) || option.indexOf('.') >= 0) {
                continue;
            }
            options.put(option, properties.getProperty(key));
        }
        return options;
    }

    /**
     * 校验配置源非空。
     *
     * @param prop 配置源
     * @throws OssException {@code prop} 为 {@code null}
     */
    private static void requireProp(Prop prop) {
        if (prop == null) {
            throw new OssException("prop can not be null");
        }
    }

    /**
     * 返回平台标识列表，仅用于异常消息。
     *
     * @param configs 平台配置
     * @return 平台标识列表
     */
    private static List<String> platformNamesOf(List<OssConfig> configs) {
        List<String> names = new ArrayList<>(configs.size());
        for (OssConfig config : configs) {
            names.add(config.platform());
        }
        return names;
    }
}
