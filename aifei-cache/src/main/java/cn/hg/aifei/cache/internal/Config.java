/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.cache.internal;

import cn.aifei.util.Prop;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 配置域唯一定义点：实例级配置对象（链式 setter）与配置项读取/校验（静态方法）同在一处。
 *
 * <p>公共接口不声明任何常量，因此框架级常量（含属数据格式的键空间保留前缀与保留名）也集中在本类。</p>
 *
 * <p>本类可自主演进；配置错误消息格式与凭据脱敏规则属对使用者的可见行为。</p>
 */
public abstract class Config implements Serializable {

    /**
     * 配置校验失败消息的固定前缀。
     */
    private static final String CONFIG_INVALID_PREFIX = "cache config invalid: ";

    /**
     * 配置值回显时替换凭据的占位串。
     */
    private static final String CREDENTIAL_MASK = "***";

    /**
     * 根级配置键前缀：主缓存的全部配置都在它下面，扩展实例为 {@code cache.<name>}。
     */
    public static final String ROOT_PREFIX = "cache";

    /**
     * 主缓存 / 主计数器的实例名，属保留名：不可重命名，也不可被 {@code cache.names} 占用。
     */
    public static final String MAIN_INSTANCE_NAME = "__MAIN__";

    /**
     * 根级配置键：Fury 反序列化放行的业务包名，逗号分隔。同一 Redis 的多个实例共用一份策略，
     * 故只认根级、不带实例前缀；键名与其它 jedis 键同处 {@code jedis} 分段下。
     */
    public static final String ALLOWED_PACKAGES_KEY = ROOT_PREFIX + ".jedis.allowedPackages";

    /**
     * 根级配置键：声明扩展实例名，逗号分隔。
     */
    public static final String NAMES_KEY = ROOT_PREFIX + ".names";

    /**
     * 未显式配置时使用的默认命名空间；扩展实例不继承主缓存的命名空间。
     */
    public static final String DEFAULT_NAMESPACE = "aifei";

    /**
     * 未显式配置时使用的默认 TTL，单位毫秒。
     */
    public static final long DEFAULT_TTL = 60000L;

    /**
     * TTL 上限毫秒值：{@link Integer#MAX_VALUE} 秒。
     */
    public static final long MAX_TTL_MILLIS = Integer.MAX_VALUE * 1000L;

    // ─────────── 键空间保留名与保留前缀：属数据格式，发布后不得更改 ───────────

    /**
     * 计数物理 key 的内部保留前缀，调用方不得占用。取值**自带结尾的分隔符**，拼装点直接后接计数
     * 名称。计数物理 key 由 {@link AbstractCounter#physicalKey(String, String, String)} 按它拼装。
     */
    public static final String COUNTER_KEY_PREFIX = "COUNTER:";

    /**
     * {@code cacheNames()} 在 Redis 后端的内部集合保留名，调用方不得占用：它是物理 key
     * {@code namespace + ":" + 本名} 的一段，属数据格式。Caffeine 后端用实例内集合，无此保留名。
     */
    public static final String CACHE_NAMES_NAME = "__CACHE_NAMES__";

    /**
     * {@code counterNames()} 在 Redis 后端的内部集合保留名，调用方不得占用：它是物理 key
     * {@code namespace + ":" + 本名} 的一段，属数据格式。Caffeine 后端用实例内集合，无此保留名。
     */
    public static final String COUNTER_NAMES_NAME = "__COUNTER_NAMES__";

    /**
     * 不可作为扩展实例名的保留词：这些名字都是根级配置项，被实例名占用后 {@code cache.<name>}
     * 会同时是一枚标量键与一段前缀，无法区分。键名变更时必须同步本表。
     */
    private static final Set<String> RESERVED_INSTANCE_NAMES = new LinkedHashSet<>(
            Arrays.asList("type", "ttlMillis", "cacheNull", "namespace", "names", "main", "allowedPackages")
    );

    /**
     * 实例名。
     */
    private String instanceName;

    /**
     * 后端类型标识。
     */
    private String type;

    /**
     * 默认 TTL，单位毫秒。
     */
    private long ttl = DEFAULT_TTL;

    /**
     * 是否缓存 {@code null} 值。
     */
    private boolean cacheNull = false;

    /**
     * 命名空间。
     */
    private String namespace = DEFAULT_NAMESPACE;

    /**
     * Fury 放行的业务包名。
     */
    private List<String> allowedPackages;

    /**
     * 本实例的配置键前缀，供 setter 拼装配置错误消息的键；主缓存为 {@code cache}，
     * 扩展实例为 {@code cache.<name>}。
     */
    private String prefix = ROOT_PREFIX;

    public String instanceName() {
        return instanceName;
    }

    /**
     * 设置实例名；校验规则见 {@link #requireRegisteredInstanceName(String)}。
     */
    public Config instanceName(String instanceName) {
        this.instanceName = requireInstanceName(instanceName, true);
        return this;
    }

    public String type() {
        return type;
    }

    public Config type(String type) {
        this.type = type;
        return this;
    }

    public long ttl() {
        return ttl;
    }

    public Config ttl(long ttl) {
        this.ttl = ttl;
        return this;
    }

    public boolean cacheNull() {
        return cacheNull;
    }

    public Config cacheNull(boolean cacheNull) {
        this.cacheNull = cacheNull;
        return this;
    }

    public String namespace() {
        return namespace;
    }

    public Config namespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public List<String> allowedPackages() {
        return allowedPackages;
    }

    public Config allowedPackages(List<String> allowedPackages) {
        this.allowedPackages = allowedPackages;
        return this;
    }

    public String prefix() {
        return prefix;
    }

    /**
     * 设置本实例的配置键前缀；由 {@code AbstractCacheProvider#initConfig} 在 {@code buildConfig}
     * 之前写入，供子类 setter 拼装配置键。
     */
    public Config prefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    /**
     * 拼装本实例某配置项的完整键。
     */
    public String configKey(String relativeKey) {
        return prefix + relativeKey;
    }

    // ─────────── 配置错误消息 ───────────

    /**
     * 构造统一格式的配置校验异常；值中的凭据先脱敏再拼入消息。
     */
    public static IllegalArgumentException configInvalid(String key, Object value, String reason) {
        return new IllegalArgumentException(
                CONFIG_INVALID_PREFIX + key + "=" + maskCredential(key, value) + ", reason=" + reason);
    }

    /**
     * 以本实例前缀补全相对键后构造统一格式的配置校验异常，供子类 setter 使用。
     *
     * <p>刻意不叫 {@code configInvalid}：静态与否不参与重载判定，同名的 {@code (String, Object,
     * String)} 实例方法会与上面的静态入口**重复定义**而编译失败。</p>
     */
    protected IllegalArgumentException configInvalidAt(String relativeKey, Object value, String reason) {
        return configInvalid(configKey(relativeKey), value, reason);
    }

    /**
     * 凭据脱敏：口令类键整体遮蔽，含 {@code userInfo} 的 URI 只保留协议与主机部分。
     */
    private static Object maskCredential(String key, Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (key.endsWith(".password")) {
            return CREDENTIAL_MASK;
        }
        int schemeEnd = text.indexOf("://");
        if (schemeEnd > 0) {
            int userInfoEnd = text.indexOf('@', schemeEnd + 3);
            if (userInfoEnd > 0) {
                return text.substring(0, schemeEnd + 3) + CREDENTIAL_MASK + text.substring(userInfoEnd);
            }
        }
        return text;
    }

    // ─────────── 配置项读取（语法解析；范围校验归各配置类的 setter） ───────────

    /**
     * 判断配置值是否未配置：{@code null} 或只含空白。
     *
     * <p>{@code Prop#get} 对只含空白的取值返回空串而非 {@code null}，必须在此归一。</p>
     */
    private static boolean isUnconfigured(String rawValue) {
        return rawValue == null || rawValue.trim().isEmpty();
    }

    /**
     * 读取可选文本；未配置返回 {@code null}。
     */
    public static String optionalText(Prop prop, String key) {
        String rawValue = prop.get(key);
        return isUnconfigured(rawValue) ? null : requireConfigText(key, rawValue);
    }

    /**
     * 读取可选布尔值；未配置返回 {@code null}。
     */
    public static Boolean optionalBoolean(Prop prop, String key) {
        String rawValue = prop.get(key);
        return isUnconfigured(rawValue) ? null : requireConfigBoolean(key, rawValue);
    }

    /**
     * 读取可选整数；未配置返回 {@code null}。只做整数语法校验，取值范围由调用方校验。
     */
    public static Integer optionalInt(Prop prop, String key) {
        String rawValue = prop.get(key);
        return isUnconfigured(rawValue) ? null : parseConfigInt(key, rawValue);
    }

    /**
     * 读取可选长整数；未配置返回 {@code null}。只做整数语法校验，取值范围由调用方校验。
     */
    public static Long optionalLong(Prop prop, String key) {
        String rawValue = prop.get(key);
        return isUnconfigured(rawValue) ? null : parseConfigLong(key, rawValue);
    }

    /**
     * 校验配置文本非空白，并返回去空白后的值。
     */
    public static String requireConfigText(String key, String raw) {
        if (raw == null) {
            throw configInvalid(key, "", "value is required");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw configInvalid(key, raw, "value must not be blank");
        }
        return trimmed;
    }

    /**
     * 校验配置布尔值，只接受 {@code true} / {@code false}（忽略大小写与首尾空白）。
     */
    private static boolean requireConfigBoolean(String key, String raw) {
        if (raw == null) {
            throw configInvalid(key, "", "value is required");
        }
        String value = raw.trim().toLowerCase();
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw configInvalid(key, raw, "must be true or false");
    }

    /**
     * 校验配置长整数并落在 TTL 允许的闭区间内；当前仅 {@code ttlMillis} 使用。
     */
    private static long requireConfigLong(String key, String raw) {
        long value = parseConfigLong(key, raw);
        if (value < 1L || value > Config.MAX_TTL_MILLIS) {
            throw configInvalid(key, raw, "must be between " + 1L + " and " + Config.MAX_TTL_MILLIS);
        }
        return value;
    }

    /**
     * 解析配置整数，非法数字写法按配置错误上报。
     */
    private static int parseConfigInt(String key, String raw) {
        long value = parseConfigLong(key, raw);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw configInvalid(key, raw,
                    "must be between " + Integer.MIN_VALUE + " and " + Integer.MAX_VALUE);
        }
        return (int) value;
    }

    /**
     * 解析配置长整数，非法数字写法按配置错误上报。
     */
    private static long parseConfigLong(String key, String raw) {
        String value = requireConfigText(key, raw);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw configInvalid(key, raw, "must be a literal integer (60_000 and 60*1000 are invalid)");
        }
    }

    // ─────────── 通用字段解析（含缺省与继承回落） ───────────

    /**
     * 解析命名空间；未配置时取 {@link #DEFAULT_NAMESPACE}，不继承主缓存。
     *
     * <p>合法性规则（非空白、不含 {@code :}）在 {@link CacheValidator#requireNamespace(String)}，本方法只
     * 负责取键，并把校验异常转成统一格式的配置错误消息。命名空间是物理 key 的第一段，含 {@code :} 会让
     * 拼接失去单射性，故在配置解析期即失败，不像名字那样存在「读静默 / 写抛异常」的两侧差异。</p>
     */
    public static String resolveNamespace(Prop prop, String prefix) {
        String key = prefix + ".namespace";
        String rawValue = prop.get(key);
        if (isUnconfigured(rawValue)) {
            return DEFAULT_NAMESPACE;
        }
        String namespace = requireConfigText(key, rawValue);
        try {
            return CacheValidator.requireNamespace(namespace);
        } catch (IllegalArgumentException e) {
            throw configInvalid(key, rawValue, e.getMessage());
        }
    }

    /**
     * 解析默认 TTL；键名为 {@code .ttlMillis}（单位毫秒）。扩展实例缺省继承主缓存，主缓存缺省取
     * {@link #DEFAULT_TTL}。「继承主缓存」表达为缺省回落到根级同名项：对主缓存而言 {@code prefix}
     * 即根前缀，回落即读自身。
     */
    public static long resolveTtl(Prop prop, String prefix) {
        String key = prefix + ".ttlMillis";
        String rawValue = prop.get(key);
        if (!isUnconfigured(rawValue)) {
            return requireConfigLong(key, rawValue);
        }
        String rootKey = ROOT_PREFIX + ".ttlMillis";
        String rootRawValue = prop.get(rootKey);
        return isUnconfigured(rootRawValue)
                ? DEFAULT_TTL
                : requireConfigLong(rootKey, rootRawValue);
    }

    /**
     * 解析 {@code cacheNull}；扩展实例缺省继承主缓存，主缓存缺省为 {@code false}，回落规则同
     * {@link #resolveTtl}。
     */
    public static boolean resolveCacheNull(Prop prop, String prefix) {
        String key = prefix + ".cacheNull";
        String rawValue = prop.get(key);
        if (!isUnconfigured(rawValue)) {
            return requireConfigBoolean(key, rawValue);
        }
        String rootKey = ROOT_PREFIX + ".cacheNull";
        String rootRawValue = prop.get(rootKey);
        return !isUnconfigured(rootRawValue) && requireConfigBoolean(rootKey, rootRawValue);
    }

    /**
     * 解析 {@link #ALLOWED_PACKAGES_KEY}：逗号分隔、逐项去空白，空项即失败；未配置返回空列表。
     */
    public static List<String> resolveAllowedPackages(Prop prop) {
        String rawValue = prop.get(ALLOWED_PACKAGES_KEY);
        List<String> packages = new ArrayList<>();
        if (isUnconfigured(rawValue)) {
            return packages;
        }
        for (String item : rawValue.split(",", -1)) {
            String packageName = item.trim();
            if (packageName.isEmpty()) {
                throw configInvalid(ALLOWED_PACKAGES_KEY, rawValue, "package name must not be blank");
            }
            packages.add(packageName);
        }
        return packages;
    }

    /**
     * 校验 {@code cache.type} 取值落在已注册的后端类型内；白名单由调用方传入，不硬编码类型名。
     */
    public static String requireCacheType(Prop prop, String key, Set<String> allowedTypes) {
        String raw = prop.get(key);
        String value = requireConfigText(key, raw);
        if (allowedTypes == null || !allowedTypes.contains(value)) {
            throw configInvalid(key, raw, "must be one of " + allowedTypes);
        }
        return value;
    }

    // ─────────── 实例名与 cache.names ───────────

    /**
     * 校验扩展实例名合法性：不得含 {@code .}、{@code =}、{@code :} 或空白，不得为保留词，
     * 也不得为主缓存保留名 {@code __MAIN__}。
     */
    public static String requireInstanceName(String instanceName) {
        return requireInstanceName(instanceName, false);
    }

    /**
     * 校验已注册实例名合法性；与 {@link #requireInstanceName(String)} 的唯一区别是放行 {@code __MAIN__}。
     */
    public static String requireRegisteredInstanceName(String instanceName) {
        return requireInstanceName(instanceName, true);
    }

    /**
     * 实例名校验的统一实现，同时供实例 setter 与两个静态入口使用。
     */
    private static String requireInstanceName(String instanceName, boolean allowMainInstanceName) {
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
        if (!allowMainInstanceName && MAIN_INSTANCE_NAME.equals(instanceName)) {
            throw new IllegalArgumentException(
                    "instance name must not be " + MAIN_INSTANCE_NAME + ": " + instanceName
            );
        }
        return instanceName;
    }

    /**
     * 判断实例名是否合法。
     */
    public static boolean isValidInstanceName(String instanceName) {
        try {
            requireInstanceName(instanceName);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 解析并校验 {@code cache.names}：逗号分隔、逐项去空白，空项与重名即失败。
     */
    public static List<String> parseInstanceNames(Prop prop, String key) {
        String raw = prop.get(key);
        List<String> names = new ArrayList<>();
        if (isUnconfigured(raw)) {
            return names;
        }
        for (String item : raw.split(",", -1)) {
            String name = item.trim();
            if (name.isEmpty()) {
                throw configInvalid(key, raw, "instance name must not be blank");
            }
            try {
                requireInstanceName(name);
            } catch (IllegalArgumentException e) {
                throw configInvalid(key, raw, e.getMessage());
            }
            if (names.contains(name)) {
                throw configInvalid(key, raw, "duplicate instance name: " + name);
            }
            names.add(name);
        }
        return names;
    }
}
