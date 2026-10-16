/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 配置域与键常量的唯一定义点：值对象持有实例名、后端类型与实例前缀，静态入口完成 {@code mq.names} 与
 * {@code <实例前缀>.type} 的解析与校验。
 *
 * <p>配置对象与全部键常量、键拼装同在一处，实现类不得手写 {@code "mq.memory."} 之类的字面量。主实例的
 * 实例前缀是 {@code mq}（配置走根级 {@code mq.*}），命名实例的前缀是 {@code mq.<name>}（配置走
 * {@code mq.<name>.*}）；后缀写 {@code .type} 得类型键，后端专有键经 {@link #get(String)} 读取。</p>
 */
public final class MqConfig {

    /**
     * 根级配置键前缀：全部配置都在它下面。
     */
    public static final String ROOT_PREFIX = "mq";

    /**
     * 主实例（根级）的后端类型配置键。
     */
    public static final String TYPE_KEY = ROOT_PREFIX + ".type";

    /**
     * 声明命名实例的配置键，逗号分隔；主实例不在此声明。
     */
    public static final String NAMES_KEY = ROOT_PREFIX + ".names";

    /**
     * 未显式配置后端类型时使用的默认类型，也是后端标识字面量的唯一来源。
     */
    public static final String DEFAULT_TYPE = "memory";

    /**
     * 主实例的登记名，属保留名：恒存在、无需在 {@code mq.names} 中声明，也不可被命名实例占用。
     */
    public static final String MAIN_INSTANCE_NAME = "__MAIN__";

    /**
     * 类型键相对实例前缀的后缀，形如 {@code <实例前缀>.type}。
     */
    private static final String TYPE_RELATIVE_KEY = "type";

    /**
     * 不可作为命名实例名的保留词：它们都是根级配置项，被实例名占用后 {@code mq.<name>} 会同时是一枚标量
     * 键与一段前缀，无法区分。键名变更时必须同步本表。
     */
    private static final Set<String> RESERVED_INSTANCE_NAMES = new LinkedHashSet<>(
            Arrays.asList("type", "names")
    );

    /**
     * 实例名；主实例为 {@link #MAIN_INSTANCE_NAME}。
     */
    private final String instanceName;

    /**
     * 后端类型。
     */
    private final String type;

    /**
     * 本实例的配置键前缀，形如 {@code mq}（主实例）或 {@code mq.<name>}（命名实例）。
     */
    private final String prefix;

    /**
     * 全部配置项；后端专有键经 {@link #get(String)} 读取。
     */
    private final Properties properties;

    /**
     * 禁止外部构造，只能经 {@link #parse(Properties, Set, String)} 创建。
     */
    private MqConfig(String instanceName, String type, String prefix, Properties properties) {
        this.instanceName = instanceName;
        this.type = type;
        this.prefix = prefix;
        this.properties = properties;
    }

    /**
     * 解析并校验 {@code mq.names}：逗号分隔、逐项去空白，空项、非法字符、保留名、{@link #MAIN_INSTANCE_NAME}
     * 与重名即失败。
     *
     * @param properties 全部配置项
     * @param key 配置键，通常取 {@link #NAMES_KEY}
     * @return 命名实例名列表，保持声明顺序；未配置时返回空列表
     * @throws IllegalArgumentException {@code properties} 为 {@code null}、{@code key} 为空、出现空白项、
     *         实例名非法 / 为保留名 / 为 {@link #MAIN_INSTANCE_NAME}，或同一清单内重名
     */
    public static List<String> parseInstanceNames(Properties properties, String key) {
        if (properties == null) {
            throw new IllegalArgumentException("properties can not be null");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key can not be blank");
        }
        String raw = properties.getProperty(key);
        List<String> names = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return names;
        }
        for (String item : raw.split(",", -1)) {
            String name = item.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(key + " contains a blank instance name: " + raw);
            }
            requireInstanceName(name);
            if (MAIN_INSTANCE_NAME.equals(name)) {
                throw new IllegalArgumentException(key + " must not declare the main instance: " + name);
            }
            if (names.contains(name)) {
                throw new IllegalArgumentException(key + " contains duplicate instance name: " + name);
            }
            names.add(name);
        }
        return names;
    }

    /**
     * 校验实例名合法性：非空白、不含 {@code .} / {@code =} / {@code :} 或空白字符、不为根级配置键保留名。
     *
     * <p>{@link #MAIN_INSTANCE_NAME} 是合法实例名（主实例的登记名），但不可由 {@code mq.names} 声明——
     * 该禁用在 {@link #parseInstanceNames(Properties, String)} 内判定。</p>
     *
     * @param instanceName 实例名
     * @return 原实例名
     * @throws IllegalArgumentException {@code instanceName} 为空、含非法字符或为保留名
     */
    public static String requireInstanceName(String instanceName) {
        if (instanceName == null || instanceName.trim().isEmpty()) {
            throw new IllegalArgumentException("instance name can not be blank");
        }
        for (int i = 0; i < instanceName.length(); i++) {
            char c = instanceName.charAt(i);
            if (c == '.' || c == '=' || c == ':' || Character.isWhitespace(c)) {
                throw new IllegalArgumentException(
                        "instance name must not contain '.', '=', ':' or whitespace: " + instanceName
                );
            }
        }
        if (RESERVED_INSTANCE_NAMES.contains(instanceName)) {
            throw new IllegalArgumentException("instance name is reserved: " + instanceName);
        }
        return instanceName;
    }

    /**
     * 解析并校验某个实例的配置：按实例名派生实例前缀，读 {@code <实例前缀>.type}，缺省取
     * {@link #DEFAULT_TYPE}，取值必须落在已注册类型内。
     *
     * @param properties 全部配置项
     * @param registeredTypes 已注册的后端类型
     * @param instanceName 实例名；主实例传 {@link #MAIN_INSTANCE_NAME}
     * @return 配置对象
     * @throws IllegalArgumentException {@code properties} 或 {@code registeredTypes} 为 {@code null}，
     *         {@code instanceName} 非法，或该实例的类型指向未注册的后端
     */
    public static MqConfig parse(Properties properties, Set<String> registeredTypes, String instanceName) {
        if (properties == null) {
            throw new IllegalArgumentException("properties can not be null");
        }
        if (registeredTypes == null) {
            throw new IllegalArgumentException("registeredTypes can not be null");
        }
        requireInstanceName(instanceName);
        String prefix = prefixOf(instanceName);
        String raw = properties.getProperty(prefix + "." + TYPE_RELATIVE_KEY);
        String type = (raw == null || raw.trim().isEmpty()) ? DEFAULT_TYPE : raw.trim();
        if (!registeredTypes.contains(type)) {
            throw new IllegalArgumentException(
                    "unsupported mq type: " + type + ", supported types: " + registeredTypes
            );
        }
        return new MqConfig(instanceName, type, prefix, properties);
    }

    /**
     * 由实例名派生配置键前缀：主实例为 {@code mq}，命名实例为 {@code mq.<name>}。
     */
    private static String prefixOf(String instanceName) {
        return MAIN_INSTANCE_NAME.equals(instanceName) ? ROOT_PREFIX : ROOT_PREFIX + "." + instanceName;
    }

    /**
     * 返回实例名。
     *
     * @return 实例名；主实例为 {@link #MAIN_INSTANCE_NAME}
     */
    public String instanceName() {
        return instanceName;
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
     * 返回本实例的配置键前缀。
     *
     * @return 形如 {@code mq} 或 {@code mq.<name>} 的实例前缀
     */
    public String prefix() {
        return prefix;
    }

    /**
     * 读取本实例本后端的某项配置；键名相对后端，无需带实例前缀与后端类型。
     *
     * @param relativeKey 相对键名
     * @return 配置值；未配置时 {@code null}
     */
    public String get(String relativeKey) {
        return properties.getProperty(prefix + "." + type + "." + relativeKey);
    }
}
