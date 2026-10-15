/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.aifei;

import cn.aifei.util.Prop;
import cn.hg.aifei.websocket.ClientConfig;
import cn.hg.aifei.websocket.ServerConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link Prop} 到 {@link ClientConfig} / {@link ServerConfig} 的唯一转换点：全部配置键名与解析逻辑集中于此，
 * 接入包内其它类型不得再出现配置键字符串。
 *
 * <p>键名采用 kebab-case，与配置对象的字段一一对应：客户端前缀为 {@code websocket.client}，服务端前缀为
 * {@code websocket.server}。未配置的键一律沿用配置对象自身的默认值（默认值只在 {@link ClientConfig} /
 * {@link ServerConfig} 内定义，本类不重复声明，只在键存在时覆写）。</p>
 *
 * <p><b>必填键</b>：{@code websocket.client.uri} 与 {@code websocket.server.port} 缺失即抛
 * {@link IllegalArgumentException}，表现为应用启动失败。{@code websocket.server.port} 的「必填」语义只在
 * 接入层施加：API 层 {@link ServerConfig#port()} 默认 {@code 0} 表示由系统分配随机端口。</p>
 *
 * <p><b>握手附加请求头</b>（{@link ClientConfig#headers()}）无法用扁平键表达，改用前缀方案：键形如
 * {@code websocket.client.header.<name>}，其中 {@code <name>} 为请求头名、键值为请求头值。该前缀方案是本类
 * 的单一定义点。</p>
 *
 * <p>取值语法非法（无法解析为整数 / 小数 / 布尔）在本类按含键名的配置错误上报；取值范围的合法性交由
 * {@link ClientConfig} / {@link ServerConfig} 的 setter 与 {@code internal.WebSocketValidator} 负责，
 * 本类不复制任何校验规则。</p>
 */
final class WebSocketConfigLoader {

    /**
     * 客户端配置键前缀。
     */
    private static final String CLIENT_PREFIX = "websocket.client";

    /**
     * 服务端配置键前缀。
     */
    private static final String SERVER_PREFIX = "websocket.server";

    /**
     * 客户端握手附加请求头的键前缀：完整键为该前缀后接请求头名。
     */
    private static final String CLIENT_HEADER_PREFIX = CLIENT_PREFIX + ".header.";

    /**
     * 配置错误消息的固定前缀。
     */
    private static final String ERROR_PREFIX = "websocket config invalid: ";

    /**
     * 禁止创建工具类实例。
     */
    private WebSocketConfigLoader() {
    }

    /**
     * 解析客户端配置。
     *
     * @param prop 配置源
     * @return 解析后的客户端配置
     * @throws IllegalArgumentException {@code prop} 为 {@code null}；必填键 {@code websocket.client.uri} 缺失；
     *         取值语法非法；或取值被 setter 判为越界、被构建期跨字段校验判为不合法
     */
    static ClientConfig loadClientConfig(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        ClientConfig config = new ClientConfig();

        Integer heartbeatSeconds = intValue(prop, CLIENT_PREFIX + ".heartbeat-seconds");
        if (heartbeatSeconds != null) {
            config.setHeartbeatSeconds(heartbeatSeconds);
        }
        String uriKey = CLIENT_PREFIX + ".uri";
        String uri = prop.get(uriKey);
        if (uri == null) {
            throw configInvalid(uriKey, "", "value is required");
        }
        config.setUri(uri);
        Integer connectTimeoutMillis = intValue(prop, CLIENT_PREFIX + ".connect-timeout-millis");
        if (connectTimeoutMillis != null) {
            config.setConnectTimeoutMillis(connectTimeoutMillis);
        }
        Boolean autoReconnect = booleanValue(prop, CLIENT_PREFIX + ".auto-reconnect");
        if (autoReconnect != null) {
            config.setAutoReconnect(autoReconnect);
        }
        Integer maxRetries = intValue(prop, CLIENT_PREFIX + ".max-retries");
        if (maxRetries != null) {
            config.setMaxRetries(maxRetries);
        }
        Long initialRetryDelayMillis = longValue(prop, CLIENT_PREFIX + ".initial-retry-delay-millis");
        if (initialRetryDelayMillis != null) {
            config.setInitialRetryDelayMillis(initialRetryDelayMillis);
        }
        Long maxRetryDelayMillis = longValue(prop, CLIENT_PREFIX + ".max-retry-delay-millis");
        if (maxRetryDelayMillis != null) {
            config.setMaxRetryDelayMillis(maxRetryDelayMillis);
        }
        Double retryJitterRatio = doubleValue(prop, CLIENT_PREFIX + ".retry-jitter-ratio");
        if (retryJitterRatio != null) {
            config.setRetryJitterRatio(retryJitterRatio);
        }
        Map<String, String> headers = readHeaders(prop);
        if (!headers.isEmpty()) {
            config.setHeaders(headers);
        }
        return config;
    }

    /**
     * 解析服务端配置。
     *
     * @param prop 配置源
     * @return 解析后的服务端配置
     * @throws IllegalArgumentException {@code prop} 为 {@code null}；必填键 {@code websocket.server.port} 缺失；
     *         取值语法非法；或取值被 setter 判为越界
     */
    static ServerConfig loadServerConfig(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop can not be null");
        }
        ServerConfig config = new ServerConfig();

        Integer heartbeatSeconds = intValue(prop, SERVER_PREFIX + ".heartbeat-seconds");
        if (heartbeatSeconds != null) {
            config.setHeartbeatSeconds(heartbeatSeconds);
        }
        String host = prop.get(SERVER_PREFIX + ".host");
        if (host != null) {
            config.setHost(host);
        }
        String portKey = SERVER_PREFIX + ".port";
        Integer port = intValue(prop, portKey);
        if (port == null) {
            throw configInvalid(portKey, "", "value is required");
        }
        config.setPort(port);
        Boolean reuseAddress = booleanValue(prop, SERVER_PREFIX + ".reuse-address");
        if (reuseAddress != null) {
            config.setReuseAddress(reuseAddress);
        }
        Integer sessionIdleTimeoutSeconds = intValue(prop, SERVER_PREFIX + ".session-idle-timeout-seconds");
        if (sessionIdleTimeoutSeconds != null) {
            config.setSessionIdleTimeoutSeconds(sessionIdleTimeoutSeconds);
        }
        Integer maxPendingConnections = intValue(prop, SERVER_PREFIX + ".max-pending-connections");
        if (maxPendingConnections != null) {
            config.setMaxPendingConnections(maxPendingConnections);
        }
        return config;
    }

    // ─────────── 值读取：未配置返回 null，语法非法按含键名的配置错误上报 ───────────

    /**
     * 读取可选整数；未配置返回 {@code null}，语法非法抛含键名的配置错误。
     *
     * @param prop 配置源
     * @param key 完整配置键
     * @return 整数取值；未配置时为 {@code null}
     * @throws IllegalArgumentException 取值无法解析为整数
     */
    private static Integer intValue(Prop prop, String key) {
        try {
            return prop.getInt(key);
        } catch (RuntimeException e) {
            throw configInvalid(key, prop.get(key), "must be an integer");
        }
    }

    /**
     * 读取可选长整数；未配置返回 {@code null}，语法非法抛含键名的配置错误。
     *
     * @param prop 配置源
     * @param key 完整配置键
     * @return 长整数取值；未配置时为 {@code null}
     * @throws IllegalArgumentException 取值无法解析为长整数
     */
    private static Long longValue(Prop prop, String key) {
        try {
            return prop.getLong(key);
        } catch (RuntimeException e) {
            throw configInvalid(key, prop.get(key), "must be an integer");
        }
    }

    /**
     * 读取可选小数；未配置返回 {@code null}，语法非法抛含键名的配置错误。
     *
     * @param prop 配置源
     * @param key 完整配置键
     * @return 小数取值；未配置时为 {@code null}
     * @throws IllegalArgumentException 取值无法解析为小数
     */
    private static Double doubleValue(Prop prop, String key) {
        try {
            return prop.getDouble(key);
        } catch (RuntimeException e) {
            throw configInvalid(key, prop.get(key), "must be a number");
        }
    }

    /**
     * 读取可选布尔值；未配置返回 {@code null}，语法非法抛含键名的配置错误。
     *
     * @param prop 配置源
     * @param key 完整配置键
     * @return 布尔取值；未配置时为 {@code null}
     * @throws IllegalArgumentException 取值不是 {@code true} / {@code false}
     */
    private static Boolean booleanValue(Prop prop, String key) {
        try {
            return prop.getBoolean(key);
        } catch (RuntimeException e) {
            throw configInvalid(key, prop.get(key), "must be true or false");
        }
    }

    /**
     * 读取 {@code websocket.client.header.<name>} 形式的握手附加请求头；未配置时返回空表。
     *
     * @param prop 配置源
     * @return 请求头名到值的映射；不为 {@code null}
     */
    private static Map<String, String> readHeaders(Prop prop) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (String key : prop.getProperties().stringPropertyNames()) {
            if (!key.startsWith(CLIENT_HEADER_PREFIX)) {
                continue;
            }
            String name = key.substring(CLIENT_HEADER_PREFIX.length());
            if (name.isEmpty()) {
                continue;
            }
            String value = prop.get(key);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    /**
     * 构造统一格式的配置错误，消息含键名与实参，便于定位到具体配置项。
     *
     * @param key 完整配置键
     * @param value 原始取值
     * @param reason 失败原因
     * @return 配置错误异常
     */
    private static IllegalArgumentException configInvalid(String key, String value, String reason) {
        return new IllegalArgumentException(ERROR_PREFIX + key + "=" + value + ", reason=" + reason);
    }
}
