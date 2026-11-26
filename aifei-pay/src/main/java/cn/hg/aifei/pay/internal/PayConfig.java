/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal;

import cn.hg.aifei.pay.PayKit;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 配置域：{@code pay.*} 键名、默认值、解析、校验与脱敏的唯一实现。
 *
 * <p>解析是装配期的一次性动作，产物是 appId → 不可变 {@link PayAppConfig} 的映射；
 * 任一校验失败立即抛 {@link cn.hg.aifei.pay.PayException}，不保留半装配态。</p>
 */
public final class PayConfig {

    /**
     * 全局键：默认应用编号。
     */
    public static final String KEY_DEFAULT_APP_ID = "pay.default-app-id";

    /**
     * 全局键：连接超时（毫秒）。
     */
    public static final String KEY_CONNECT_TIMEOUT = "pay.connect-timeout";

    /**
     * 全局键：读取超时（毫秒）。
     */
    public static final String KEY_READ_TIMEOUT = "pay.read-timeout";

    /**
     * 全局键：页面类下单的 HTTP 方法。
     */
    public static final String KEY_PAGE_HTTP_METHOD = "pay.page-http-method";

    /**
     * 全局键：默认异步回调地址。
     */
    public static final String KEY_NOTIFY_URL = "pay.notify-url";

    /**
     * 全局键：默认同步回调地址。
     */
    public static final String KEY_RETURN_URL = "pay.return-url";

    /**
     * 单应用键前缀。
     */
    public static final String APP_PREFIX = "pay.apps.";

    /**
     * 环境标签：沙箱。
     */
    public static final String ENV_SANDBOX = "sandbox";

    /**
     * 环境标签：生产。
     */
    public static final String ENV_PRODUCTION = "production";

    /**
     * 默认连接超时（毫秒）。
     */
    public static final String DEFAULT_CONNECT_TIMEOUT = "5000";

    /**
     * 默认读取超时（毫秒）。支付网关往返较长，较出站 HTTP 的 5000 放宽。
     */
    public static final String DEFAULT_READ_TIMEOUT = "15000";

    /**
     * 页面类下单的默认 HTTP 方法：自提交表单，无 URL 长度限制。
     */
    public static final String DEFAULT_PAGE_HTTP_METHOD = "POST";

    /**
     * 默认签名方式：公钥证书模式。
     */
    public static final String DEFAULT_SIGN_MODE = PayAppConfig.SIGN_MODE_CERT;

    /**
     * 默认字符集（内核仅支持 UTF-8）。
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /**
     * 默认签名类型（内核仅支持 RSA2）。
     */
    public static final String DEFAULT_SIGN_TYPE = "RSA2";

    /**
     * HTTP 方法：GET。
     */
    public static final String PAGE_HTTP_GET = "GET";

    /**
     * HTTP 方法：POST。
     */
    public static final String PAGE_HTTP_POST = "POST";

    /**
     * 网关 scheme 的合法值（沙箱与生产官方网关均为 https）。
     */
    private static final String SCHEME_HTTPS = "https";

    /**
     * 单应用键后缀。
     */
    private static final String SUFFIX_ENV = ".env";
    private static final String SUFFIX_GATEWAY_URL = ".gateway-url";
    private static final String SUFFIX_SIGN_MODE = ".sign-mode";
    private static final String SUFFIX_PRIVATE_KEY = ".private-key";
    private static final String SUFFIX_APP_CERT = ".app-cert";
    private static final String SUFFIX_ROOT_CERT = ".root-cert";
    private static final String SUFFIX_CHARSET = ".charset";
    private static final String SUFFIX_SIGN_TYPE = ".sign-type";
    private static final String SUFFIX_VERIFY = ".verify.";
    private static final String SUFFIX_VERIFY_TYPE = ".type";
    private static final String SUFFIX_VERIFY_VALUE = ".value";
    private static final String SUFFIX_VERIFY_CERT_SN = ".cert-sn";

    private final Map<String, PayAppConfig> apps;

    private final String defaultAppId;

    private PayConfig(Map<String, PayAppConfig> apps, String defaultAppId) {
        this.apps = Collections.unmodifiableMap(new LinkedHashMap<>(apps));
        this.defaultAppId = defaultAppId;
    }

    /**
     * 解析并校验配置。
     *
     * @param properties 配置源
     * @return 配置域
     * @throws IllegalArgumentException {@code properties} 为 {@code null}
     * @throws cn.hg.aifei.pay.PayException 配置缺失或非法
     */
    public static PayConfig parse(Properties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties 不能为 null");
        }
        int connectTimeout = positiveInt(KEY_CONNECT_TIMEOUT,
                text(properties, KEY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT));
        int readTimeout = positiveInt(KEY_READ_TIMEOUT,
                text(properties, KEY_READ_TIMEOUT, DEFAULT_READ_TIMEOUT));
        String pageHttpMethod = pageHttpMethod(properties);
        String notifyUrl = optional(properties, KEY_NOTIFY_URL);
        String returnUrl = optional(properties, KEY_RETURN_URL);
        String defaultAppId = optional(properties, KEY_DEFAULT_APP_ID);

        Set<String> appIds = appIds(properties);
        if (appIds.isEmpty()) {
            throw PayErrors.missingConfig(APP_PREFIX + "<appId>");
        }
        Map<String, PayAppConfig> apps = new LinkedHashMap<>();
        for (String appId : appIds) {
            apps.put(appId, appConfig(properties, appId, connectTimeout, readTimeout, pageHttpMethod,
                    notifyUrl, returnUrl));
        }
        return new PayConfig(apps, defaultAppId);
    }

    /**
     * @return appId → 应用配置（不可变）
     */
    public Map<String, PayAppConfig> apps() {
        return apps;
    }

    /**
     * @return 配置的默认应用编号，未配置时为 {@code null}
     */
    public String defaultAppId() {
        return defaultAppId;
    }

    /**
     * @return 是否存在证书模式的应用（决定装配期是否必须探测 BouncyCastle）
     */
    public boolean certMode() {
        for (PayAppConfig app : apps.values()) {
            if (app.isCertMode()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 appId：{@code null} / 空白回退默认应用。
     *
     * @param appId 应用编号
     * @return 应用配置
     * @throws cn.hg.aifei.pay.PayException 未配置默认应用或 appId 不存在
     */
    public PayAppConfig resolve(String appId) {
        String resolved = isBlank(appId) ? defaultAppId : appId.trim();
        if (isBlank(resolved)) {
            throw PayErrors.missingConfig(KEY_DEFAULT_APP_ID);
        }
        PayAppConfig app = apps.get(resolved);
        if (app == null) {
            throw PayErrors.unknownApp(resolved);
        }
        return app;
    }

    /**
     * 判断字符串是否为空或仅含空白。
     *
     * @param value 字符串
     * @return 空 / 空白 / {@code null} 时 {@code true}
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 收集全部应用编号（按字典序，保证解析顺序可复现）。
     *
     * @param properties 配置源
     * @return 应用编号集合
     */
    private static Set<String> appIds(Properties properties) {
        Set<String> appIds = new TreeSet<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(APP_PREFIX)) {
                continue;
            }
            String rest = name.substring(APP_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot < 0) {
                throw PayErrors.invalidConfig(name, null, "缺少应用级子键，形如 " + APP_PREFIX + "<appId>.env");
            }
            String appId = rest.substring(0, dot).trim();
            if (appId.isEmpty()) {
                throw PayErrors.invalidConfig(name, null, "应用编号不能为空");
            }
            appIds.add(appId);
        }
        return appIds;
    }

    /**
     * 解析单个应用的全部键并校验。
     *
     * @param properties     配置源
     * @param appId          应用编号
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @param pageHttpMethod 页面类下单的 HTTP 方法
     * @param notifyUrl      全局默认异步回调地址
     * @param returnUrl      全局默认同步回调地址
     * @return 应用配置
     */
    private static PayAppConfig appConfig(Properties properties, String appId, int connectTimeout, int readTimeout,
                                          String pageHttpMethod, String notifyUrl, String returnUrl) {
        String prefix = APP_PREFIX + appId;
        String env = env(properties, prefix);
        String gatewayUrl = gatewayUrl(properties, prefix, env);
        String signMode = signMode(properties, prefix);
        String privateKey = PayResource.read(prefix + SUFFIX_PRIVATE_KEY, optional(properties,
                prefix + SUFFIX_PRIVATE_KEY));
        boolean certMode = PayAppConfig.SIGN_MODE_CERT.equals(signMode);
        String appCert = certMode
                ? PayResource.read(prefix + SUFFIX_APP_CERT, optional(properties, prefix + SUFFIX_APP_CERT))
                : null;
        String rootCert = certMode
                ? PayResource.read(prefix + SUFFIX_ROOT_CERT, optional(properties, prefix + SUFFIX_ROOT_CERT))
                : null;
        String charset = charset(properties, prefix);
        String signType = signType(properties, prefix);
        List<PayVerifyKey> verifyKeys = verifyKeys(properties, prefix);
        return new PayAppConfig(appId, env, gatewayUrl, signMode, privateKey, appCert, rootCert, charset, signType,
                connectTimeout, readTimeout, pageHttpMethod, notifyUrl, returnUrl, verifyKeys);
    }

    /**
     * 解析并校验环境标签。
     *
     * @param properties 配置源
     * @param prefix     应用键前缀
     * @return 归一化后的环境标签
     */
    private static String env(Properties properties, String prefix) {
        String key = prefix + SUFFIX_ENV;
        String env = optional(properties, key);
        if (isBlank(env)) {
            throw PayErrors.missingConfig(key);
        }
        if (ENV_SANDBOX.equalsIgnoreCase(env)) {
            return ENV_SANDBOX;
        }
        if (ENV_PRODUCTION.equalsIgnoreCase(env)) {
            return ENV_PRODUCTION;
        }
        throw PayErrors.invalidConfig(key, env, "合法值为 " + ENV_SANDBOX + " / " + ENV_PRODUCTION);
    }

    /**
     * 解析并校验网关：缺省取环境对应官方网关；显式配置时 scheme 与 host 必须与环境一致。
     *
     * @param properties 配置源
     * @param prefix     应用键前缀
     * @param env        环境标签
     * @return 网关地址
     */
    private static String gatewayUrl(Properties properties, String prefix, String env) {
        String official = ENV_SANDBOX.equals(env) ? PayKit.GATEWAY_SANDBOX : PayKit.GATEWAY_PRODUCTION;
        String key = prefix + SUFFIX_GATEWAY_URL;
        String configured = optional(properties, key);
        if (isBlank(configured)) {
            return official;
        }
        URI uri;
        try {
            uri = new URI(configured);
        } catch (URISyntaxException e) {
            throw PayErrors.invalidConfig(key, configured, "不是合法的 URL");
        }
        if (!SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme())) {
            throw PayErrors.invalidConfig(key, configured, "scheme 必须为 " + SCHEME_HTTPS);
        }
        String expectedHost = hostOf(official);
        String actualHost = uri.getHost() == null ? "" : uri.getHost();
        if (!actualHost.equalsIgnoreCase(expectedHost)) {
            throw PayErrors.gatewayEnvMismatch(env, expectedHost, actualHost);
        }
        return configured;
    }

    /**
     * 解析并校验签名方式。
     *
     * @param properties 配置源
     * @param prefix     应用键前缀
     * @return 归一化后的签名方式
     */
    private static String signMode(Properties properties, String prefix) {
        String key = prefix + SUFFIX_SIGN_MODE;
        String signMode = text(properties, key, DEFAULT_SIGN_MODE).toLowerCase(Locale.ROOT);
        if (PayAppConfig.SIGN_MODE_CERT.equals(signMode) || PayAppConfig.SIGN_MODE_KEY.equals(signMode)) {
            return signMode;
        }
        throw PayErrors.invalidConfig(key, signMode, "合法值为 " + PayAppConfig.SIGN_MODE_CERT + " / "
                + PayAppConfig.SIGN_MODE_KEY);
    }

    /**
     * 解析并校验字符集（内核仅支持 UTF-8）。
     *
     * @param properties 配置源
     * @param prefix     应用键前缀
     * @return 归一化后的字符集
     */
    private static String charset(Properties properties, String prefix) {
        String key = prefix + SUFFIX_CHARSET;
        String charset = text(properties, key, DEFAULT_CHARSET);
        if (!DEFAULT_CHARSET.equalsIgnoreCase(charset)) {
            throw PayErrors.invalidConfig(key, charset, "内核仅支持 " + DEFAULT_CHARSET);
        }
        return DEFAULT_CHARSET;
    }

    /**
     * 解析并校验签名类型（内核仅支持 RSA2）。
     *
     * @param properties 配置源
     * @param prefix     应用键前缀
     * @return 归一化后的签名类型
     */
    private static String signType(Properties properties, String prefix) {
        String key = prefix + SUFFIX_SIGN_TYPE;
        String signType = text(properties, key, DEFAULT_SIGN_TYPE).toUpperCase(Locale.ROOT);
        if (!DEFAULT_SIGN_TYPE.equals(signType)) {
            throw PayErrors.invalidConfig(key, signType, "内核仅支持 " + DEFAULT_SIGN_TYPE);
        }
        return DEFAULT_SIGN_TYPE;
    }

    /**
     * 解析并校验页面类下单的 HTTP 方法。
     *
     * @param properties 配置源
     * @return 归一化后的 HTTP 方法
     */
    private static String pageHttpMethod(Properties properties) {
        String method = text(properties, KEY_PAGE_HTTP_METHOD, DEFAULT_PAGE_HTTP_METHOD).toUpperCase(Locale.ROOT);
        if (PAGE_HTTP_GET.equals(method) || PAGE_HTTP_POST.equals(method)) {
            return method;
        }
        throw PayErrors.invalidConfig(KEY_PAGE_HTTP_METHOD, method,
                "合法值为 " + PAGE_HTTP_GET + " / " + PAGE_HTTP_POST);
    }

    /**
     * 解析有序验签凭据列表：序号必须从 0 起连续，类型与取值必须成对。
     *
     * @param properties 配置源
     * @param prefix     应用键前缀
     * @return 有序凭据列表
     */
    private static List<PayVerifyKey> verifyKeys(Properties properties, String prefix) {
        String verifyPrefix = prefix + SUFFIX_VERIFY;
        TreeMap<Integer, Boolean> indexes = new TreeMap<>();
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(verifyPrefix)) {
                continue;
            }
            String rest = name.substring(verifyPrefix.length());
            int dot = rest.indexOf('.');
            String indexText = dot < 0 ? rest : rest.substring(0, dot);
            indexes.put(verifyIndex(name, indexText), Boolean.TRUE);
        }
        if (indexes.isEmpty()) {
            throw PayErrors.missingConfig(verifyPrefix + "0" + SUFFIX_VERIFY_TYPE);
        }
        int expected = 0;
        for (Integer index : indexes.keySet()) {
            if (index != expected) {
                throw PayErrors.invalidConfig(verifyPrefix + expected + SUFFIX_VERIFY_TYPE, null,
                        "凭据序号必须从 0 起连续，缺少序号 " + expected);
            }
            expected++;
        }
        List<PayVerifyKey> keys = new ArrayList<>();
        for (Integer index : indexes.keySet()) {
            String base = verifyPrefix + index;
            String typeKey = base + SUFFIX_VERIFY_TYPE;
            String valueKey = base + SUFFIX_VERIFY_VALUE;
            String typeText = optional(properties, typeKey);
            String rawValue = optional(properties, valueKey);
            if (isBlank(typeText)) {
                throw PayErrors.missingConfig(typeKey);
            }
            if (isBlank(rawValue)) {
                throw PayErrors.missingConfig(valueKey);
            }
            keys.add(PayVerifyKey.of(base, typeText, rawValue, optional(properties,
                    base + SUFFIX_VERIFY_CERT_SN)));
        }
        return keys;
    }

    /**
     * 解析凭据序号。
     *
     * @param name      完整键名（用于异常消息）
     * @param indexText 序号文本
     * @return 序号
     */
    private static int verifyIndex(String name, String indexText) {
        try {
            int index = Integer.parseInt(indexText.trim());
            if (index < 0) {
                throw PayErrors.invalidConfig(name, indexText, "凭据序号必须为非负整数");
            }
            return index;
        } catch (NumberFormatException e) {
            throw PayErrors.invalidConfig(name, indexText, "凭据序号必须为非负整数");
        }
    }

    /**
     * 取字符串配置值，空 / 空白时回落默认值。
     *
     * @param properties   配置源
     * @param key          键名
     * @param defaultValue 默认值
     * @return 配置值
     */
    private static String text(Properties properties, String key, String defaultValue) {
        String value = optional(properties, key);
        return isBlank(value) ? defaultValue : value;
    }

    /**
     * 取可选字符串配置值。
     *
     * @param properties 配置源
     * @param key        键名
     * @return 去首尾空白的值；未配置时为 {@code null}
     */
    private static String optional(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? null : value.trim();
    }

    /**
     * 解析正整数配置值。
     *
     * @param key  键名
     * @param text 值文本
     * @return 正整数值
     */
    private static int positiveInt(String key, String text) {
        try {
            int value = Integer.parseInt(text);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            throw PayErrors.invalidConfig(key, text, "必须为正整数（毫秒）");
        }
        throw PayErrors.invalidConfig(key, text, "必须为正整数（毫秒）");
    }

    /**
     * 取 URL 的 host（小写）。
     *
     * @param url URL 文本
     * @return host；无法解析时为空串
     */
    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return "";
        }
    }
}
