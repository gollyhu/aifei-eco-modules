/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * 配置域：键名常量、默认值与严格解析的唯一实现点（非承诺 API）。
 *
 * <p>只读原始字符串自行解析：框架 {@code Prop#getBoolean} 的非法值消息不含键名，不满足「启动失败须指出键名与原值」。
 * 校验顺序固定：enabled → fail-fast → args.enabled → args.max-length → client.enabled → module →
 * scan.packages → scan.classes，顺序即错误优先级。</p>
 */
public final class AuditConfig {

    static final String ENABLED_KEY = "audit.enabled";

    static final String FAIL_FAST_KEY = "audit.fail-fast";

    static final String ARGS_ENABLED_KEY = "audit.args.enabled";

    static final String ARGS_MAX_LENGTH_KEY = "audit.args.max-length";

    static final String CLIENT_ENABLED_KEY = "audit.client.enabled";

    static final String MODULE_KEY = "audit.module";

    static final String SCAN_PACKAGES_KEY = "audit.scan.packages";

    static final String SCAN_CLASSES_KEY = "audit.scan.classes";

    private static final boolean DEFAULT_ENABLED = true;

    private static final boolean DEFAULT_FAIL_FAST = false;

    private static final boolean DEFAULT_ARGS_ENABLED = false;

    private static final int DEFAULT_MAX_LENGTH = 512;

    private static final boolean DEFAULT_CLIENT_ENABLED = false;

    private static final String RULE_BOOLEAN = "只接受 true / false";

    private static final String RULE_NON_NEGATIVE = "要求 >= 0";

    private static final String RULE_PACKAGE = "每项须为非空包名";

    private static final String RULE_CLASS = "每项须为全限定类名";

    private final boolean enabled;

    private final boolean failFast;

    private final boolean argsEnabled;

    private final int maxLength;

    private final boolean clientEnabled;

    private final String module;

    private final List<String> scanPackages;

    private final List<String> scanClasses;

    private AuditConfig(boolean enabled, boolean failFast, boolean argsEnabled, int maxLength,
                        boolean clientEnabled, String module, List<String> scanPackages,
                        List<String> scanClasses) {
        this.enabled = enabled;
        this.failFast = failFast;
        this.argsEnabled = argsEnabled;
        this.maxLength = maxLength;
        this.clientEnabled = clientEnabled;
        this.module = module;
        this.scanPackages = scanPackages;
        this.scanClasses = scanClasses;
    }

    /**
     * 解析并校验全部已出现的配置项；缺省项取默认值，空白值视同缺省。
     *
     * @param props 配置源，可为 null（全部取默认值）
     * @return 不可变配置对象
     * @throws cn.hg.aifei.audit.AuditException 任一键取值非法
     */
    public static AuditConfig parse(Properties props) {
        boolean enabled = parseBoolean(props, ENABLED_KEY, DEFAULT_ENABLED);
        boolean failFast = parseBoolean(props, FAIL_FAST_KEY, DEFAULT_FAIL_FAST);
        boolean argsEnabled = parseBoolean(props, ARGS_ENABLED_KEY, DEFAULT_ARGS_ENABLED);
        int maxLength = parseNonNegativeInt(props, ARGS_MAX_LENGTH_KEY, DEFAULT_MAX_LENGTH);
        boolean clientEnabled = parseBoolean(props, CLIENT_ENABLED_KEY, DEFAULT_CLIENT_ENABLED);
        String module = normalize(text(props, MODULE_KEY));
        List<String> scanPackages = parseItems(props, SCAN_PACKAGES_KEY, RULE_PACKAGE);
        List<String> scanClasses = parseItems(props, SCAN_CLASSES_KEY, RULE_CLASS);
        return new AuditConfig(enabled, failFast, argsEnabled, maxLength, clientEnabled, module,
                scanPackages, scanClasses);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean failFast() {
        return failFast;
    }

    public boolean argsEnabled() {
        return argsEnabled;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean clientEnabled() {
        return clientEnabled;
    }

    /**
     * @return 业务模块标识；未配置或空白为 {@code null}
     */
    public String module() {
        return module;
    }

    public List<String> scanPackages() {
        return scanPackages;
    }

    public List<String> scanClasses() {
        return scanClasses;
    }

    private static String text(Properties props, String key) {
        return props == null ? null : props.getProperty(key);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean parseBoolean(Properties props, String key, boolean defaultValue) {
        String raw = text(props, key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw AuditErrors.configInvalid(key, value, RULE_BOOLEAN);
    }

    /**
     * 只认十进制非负整数：逐字符校验，避免 {@code NumberFormatException} 把配置错误变成运行期缺陷。
     */
    private static int parseNonNegativeInt(Properties props, String key, int defaultValue) {
        String raw = text(props, key);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        String value = raw.trim();
        long parsed = 0L;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                throw AuditErrors.configInvalid(key, value, RULE_NON_NEGATIVE);
            }
            parsed = parsed * 10L + (c - '0');
            if (parsed > Integer.MAX_VALUE) {
                throw AuditErrors.configInvalid(key, value, RULE_NON_NEGATIVE);
            }
        }
        return (int) parsed;
    }

    private static List<String> parseItems(Properties props, String key, String rule) {
        String raw = text(props, key);
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split(",", -1);
        List<String> items = new ArrayList<String>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String item = parts[i].trim();
            if (item.isEmpty()) {
                throw AuditErrors.configInvalid(key, raw, rule);
            }
            items.add(item);
        }
        return Collections.unmodifiableList(items);
    }
}
