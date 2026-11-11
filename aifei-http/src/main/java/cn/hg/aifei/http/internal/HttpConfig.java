/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;
import java.util.Properties;

/**
 * 出站 HTTP 的配置域：{@code http.*} 七个键的键名、默认值、解析与校验全部在本类定义，其余类只读不解析。
 *
 * <p>所有键均可不配，缺失即取默认值；取值非法时以 {@link HttpException} 失败，消息含键名与原值，便于定位。</p>
 */
public final class HttpConfig {

    /**
     * 建连超时键名（毫秒）。
     */
    public static final String KEY_CONNECT_TIMEOUT = "http.connect-timeout";

    /**
     * 读超时键名（毫秒）。
     */
    public static final String KEY_READ_TIMEOUT = "http.read-timeout";

    /**
     * 幂等请求的默认重试次数键名。
     */
    public static final String KEY_RETRY_TIMES = "http.retry-times";

    /**
     * 每次重试前的固定等待键名（毫秒）。
     */
    public static final String KEY_RETRY_INTERVAL = "http.retry-interval";

    /**
     * 单次响应体读取上限键名（解压后字节数）。
     */
    public static final String KEY_MAX_RESPONSE_BYTES = "http.max-response-bytes";

    /**
     * 是否接受 gzip 响应键名。
     */
    public static final String KEY_GZIP = "http.gzip";

    /**
     * 默认 User-Agent 键名。
     */
    public static final String KEY_USER_AGENT = "http.user-agent";

    /**
     * 建连超时默认值（毫秒）。
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;

    /**
     * 读超时默认值（毫秒）。
     */
    public static final int DEFAULT_READ_TIMEOUT = 10000;

    /**
     * 默认重试次数。
     */
    public static final int DEFAULT_RETRY_TIMES = 0;

    /**
     * 默认重试间隔（毫秒）。
     */
    public static final long DEFAULT_RETRY_INTERVAL = 200L;

    /**
     * 响应体上限默认值（8 MiB，解压后字节数）。
     */
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    /**
     * 是否接受 gzip 的默认值。
     */
    public static final boolean DEFAULT_GZIP = false;

    /**
     * 默认 User-Agent。
     */
    public static final String DEFAULT_USER_AGENT = "aifei-http/1.0.0";

    private final int connectTimeout;

    private final int readTimeout;

    private final int retryTimes;

    private final long retryInterval;

    private final int maxResponseBytes;

    private final boolean gzip;

    private final String userAgent;

    private HttpConfig(int connectTimeout, int readTimeout, int retryTimes, long retryInterval,
                       int maxResponseBytes, boolean gzip, String userAgent) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.retryTimes = retryTimes;
        this.retryInterval = retryInterval;
        this.maxResponseBytes = maxResponseBytes;
        this.gzip = gzip;
        this.userAgent = userAgent;
    }

    /**
     * 解析并校验配置；任一键取值非法即失败，不产出半成品。
     *
     * @param properties 配置源，可为 {@code null}（等价全默认）
     * @return 配置值对象
     * @throws HttpException 任一键取值非法（消息含键名与原值）
     */
    public static HttpConfig parse(Properties properties) {
        Properties source = properties == null ? new Properties() : properties;
        int connectTimeout = positiveInt(source, KEY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT, false);
        int readTimeout = positiveInt(source, KEY_READ_TIMEOUT, DEFAULT_READ_TIMEOUT, false);
        int retryTimes = positiveInt(source, KEY_RETRY_TIMES, DEFAULT_RETRY_TIMES, true);
        long retryInterval = nonNegativeLong(source, KEY_RETRY_INTERVAL, DEFAULT_RETRY_INTERVAL);
        int maxResponseBytes = positiveInt(source, KEY_MAX_RESPONSE_BYTES, DEFAULT_MAX_RESPONSE_BYTES, false);
        boolean gzip = bool(source, KEY_GZIP, DEFAULT_GZIP);
        String userAgent = text(source, KEY_USER_AGENT, DEFAULT_USER_AGENT);
        return new HttpConfig(connectTimeout, readTimeout, retryTimes, retryInterval,
                maxResponseBytes, gzip, userAgent);
    }

    /**
     * 建连超时（毫秒）。
     *
     * @return 允许为 0 以外正整数的建连超时
     */
    public int connectTimeout() {
        return connectTimeout;
    }

    /**
     * 读超时（毫秒）。
     *
     * @return 读超时
     */
    public int readTimeout() {
        return readTimeout;
    }

    /**
     * 幂等请求的默认重试次数；非幂等方法不适用。
     *
     * @return 重试次数，最小为 0
     */
    public int retryTimes() {
        return retryTimes;
    }

    /**
     * 每次重试前的固定等待（毫秒）。
     *
     * @return 等待毫秒数，最小为 0
     */
    public long retryInterval() {
        return retryInterval;
    }

    /**
     * 单次响应体读取上限（解压后字节数）。
     *
     * @return 上限字节数，最小为 1
     */
    public int maxResponseBytes() {
        return maxResponseBytes;
    }

    /**
     * 是否接受 gzip 响应（决定是否发送 {@code Accept-Encoding} 并解压）。
     *
     * @return 接受时 {@code true}
     */
    public boolean gzip() {
        return gzip;
    }

    /**
     * 默认 User-Agent；空白串表示模块不主动设置该头。
     *
     * @return User-Agent 取值，非 {@code null}
     */
    public String userAgent() {
        return userAgent;
    }

    /**
     * 读取正整数配置项。
     *
     * @param source       配置源
     * @param key          键名
     * @param defaultValue 默认值
     * @param zeroAllowed  是否允许 0
     * @return 生效值
     */
    private static int positiveInt(Properties source, String key, int defaultValue, boolean zeroAllowed) {
        String raw = source.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid(key, raw, zeroAllowed ? ">= 0" : ">= 1");
        }
        int min = zeroAllowed ? 0 : 1;
        if (value < min) {
            throw invalid(key, raw, zeroAllowed ? ">= 0" : ">= 1");
        }
        return value;
    }

    /**
     * 读取非负 long 配置项。
     *
     * @param source       配置源
     * @param key          键名
     * @param defaultValue 默认值
     * @return 生效值
     */
    private static long nonNegativeLong(Properties source, String key, long defaultValue) {
        String raw = source.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw invalid(key, raw, ">= 0");
        }
        if (value < 0L) {
            throw invalid(key, raw, ">= 0");
        }
        return value;
    }

    /**
     * 读取布尔配置项，取值大小写不敏感。
     *
     * @param source       配置源
     * @param key          键名
     * @param defaultValue 默认值
     * @return 生效值
     */
    private static boolean bool(Properties source, String key, boolean defaultValue) {
        String raw = source.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        String value = raw.trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw invalid(key, raw, "true 或 false");
    }

    /**
     * 读取文本配置项，空白串合法。
     *
     * @param source       配置源
     * @param key          键名
     * @param defaultValue 默认值
     * @return 生效值（已去首尾空白），非 {@code null}
     */
    private static String text(Properties source, String key, String defaultValue) {
        String raw = source.getProperty(key);
        return raw == null ? defaultValue : raw.trim();
    }

    /**
     * 构造取值非法的失败，消息含键名、原值与要求。
     *
     * @param key    键名
     * @param raw    原值
     * @param expect 取值要求
     * @return 失败异常
     */
    private static HttpException invalid(String key, String raw, String expect) {
        return new HttpException("配置项 " + key + " 非法: " + raw + "（要求 " + expect + "）");
    }
}
