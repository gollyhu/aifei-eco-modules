/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.aifei;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.aifei.util.Prop;
import cn.aifei.websocket.ClientConfig;
import cn.aifei.websocket.ServerConfig;
import java.util.Map;
import org.junit.Test;

/**
 * {@link WebSocketConfigLoader} 的默认值来源、键覆盖、必填键与越界处理（设计 §4.9 / §4.12）。
 *
 * <p>{@code WebSocketConfigLoader} 是包级私有类型，故本测试与源文件同包。默认值与取值范围校验的唯一
 * 定义点在配置对象与 {@code internal.WebSocketValidator}，本类只断言「未配置沿用默认值、已配置按 setter
 * 规则生效」。</p>
 */
public class WebSocketConfigLoaderTest {

    /**
     * 只配置必填的地址键时，其余客户端字段沿用 {@link ClientConfig} 的默认值。
     */
    @Test
    public void shouldUseClientDefaultsWhenOnlyUriConfigured() {
        ClientConfig config = WebSocketConfigLoader.loadClientConfig(prop("websocket.client.uri", "ws://127.0.0.1:9000/"));

        assertEquals("ws://127.0.0.1:9000/", config.uri());
        assertEquals("javawebsocket", config.type());
        assertEquals(0, config.heartbeatSeconds());
        assertNull(config.codec());
        assertEquals(5000, config.connectTimeoutMillis());
        assertFalse(config.autoReconnect());
        assertEquals(5, config.maxRetries());
        assertEquals(500L, config.initialRetryDelayMillis());
        assertEquals(30000L, config.maxRetryDelayMillis());
        assertEquals(0.2, config.retryJitterRatio(), 0.0);
        assertTrue(config.headers().isEmpty());
    }

    /**
     * 已配置的客户端键必须覆盖默认值，并落到对应字段。
     */
    @Test
    public void shouldOverrideClientKeys() {
        ClientConfig config = WebSocketConfigLoader.loadClientConfig(prop(
                "websocket.client.uri", "wss://example.test:8443/ws",
                "websocket.client.type", "javawebsocket",
                "websocket.client.heartbeat-seconds", "30",
                "websocket.client.connect-timeout-millis", "1500",
                "websocket.client.auto-reconnect", "true",
                "websocket.client.max-retries", "9",
                "websocket.client.initial-retry-delay-millis", "120",
                "websocket.client.max-retry-delay-millis", "60000",
                "websocket.client.retry-jitter-ratio", "0.35"));

        assertEquals("wss://example.test:8443/ws", config.uri());
        assertEquals(30, config.heartbeatSeconds());
        assertEquals(1500, config.connectTimeoutMillis());
        assertTrue(config.autoReconnect());
        assertEquals(9, config.maxRetries());
        assertEquals(120L, config.initialRetryDelayMillis());
        assertEquals(60000L, config.maxRetryDelayMillis());
        assertEquals(0.35, config.retryJitterRatio(), 0.0);
    }

    /**
     * {@code websocket.client.header.<name>} 前缀键被解析为握手附加请求头，前缀本身不进入头名。
     */
    @Test
    public void shouldParseClientHeaderPrefix() {
        ClientConfig config = WebSocketConfigLoader.loadClientConfig(prop(
                "websocket.client.uri", "ws://127.0.0.1:9000/",
                "websocket.client.header.Authorization", "Bearer t0ken",
                "websocket.client.header.X-Tenant", "tenant-1"));

        Map<String, String> headers = config.headers();
        assertEquals(2, headers.size());
        assertEquals("Bearer t0ken", headers.get("Authorization"));
        assertEquals("tenant-1", headers.get("X-Tenant"));
    }

    /**
     * 前缀后没有头名的键（如 {@code websocket.client.header.}）被忽略，不产出空头名。
     */
    @Test
    public void shouldIgnoreHeaderKeyWithoutName() {
        ClientConfig config = WebSocketConfigLoader.loadClientConfig(prop(
                "websocket.client.uri", "ws://127.0.0.1:9000/",
                "websocket.client.header.", "value"));

        assertTrue(config.headers().isEmpty());
    }

    /**
     * 必填键 {@code websocket.client.uri} 缺失即抛错，表现为应用启动失败。
     */
    @Test
    public void shouldRejectMissingClientUri() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> WebSocketConfigLoader.loadClientConfig(prop("websocket.client.type", "javawebsocket")));
        assertTrue("异常消息应含缺失的键名", exception.getMessage().contains("websocket.client.uri"));
    }

    /**
     * 客户端取值语法非法（无法解析为整数）按含键名的配置错误上报。
     */
    @Test
    public void shouldRejectNonNumericClientValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> WebSocketConfigLoader.loadClientConfig(prop(
                        "websocket.client.uri", "ws://127.0.0.1:9000/",
                        "websocket.client.max-retries", "abc")));
        assertTrue("异常消息应含出错的键名", exception.getMessage().contains("websocket.client.max-retries"));
    }

    /**
     * 客户端取值越界由 setter 的唯一定义点判失败，loader 不复制校验规则。
     */
    @Test
    public void shouldRejectOutOfRangeClientValue() {
        assertThrows(IllegalArgumentException.class, () -> WebSocketConfigLoader.loadClientConfig(prop(
                "websocket.client.uri", "ws://127.0.0.1:9000/",
                "websocket.client.connect-timeout-millis", "0")));
        assertThrows(IllegalArgumentException.class, () -> WebSocketConfigLoader.loadClientConfig(prop(
                "websocket.client.uri", "ws://127.0.0.1:9000/",
                "websocket.client.retry-jitter-ratio", "1.5")));
    }

    /**
     * 配置源为 {@code null} 属用户错误。
     */
    @Test
    public void shouldRejectNullProp() {
        assertThrows(IllegalArgumentException.class, () -> WebSocketConfigLoader.loadClientConfig(null));
        assertThrows(IllegalArgumentException.class, () -> WebSocketConfigLoader.loadServerConfig(null));
    }

    /**
     * 只配置必填的端口键时，其余服务端字段沿用 {@link ServerConfig} 的默认值；端口 {@code 0} 合法。
     */
    @Test
    public void shouldUseServerDefaultsWhenOnlyPortConfigured() {
        ServerConfig config = WebSocketConfigLoader.loadServerConfig(prop("websocket.server.port", "0"));

        assertEquals(0, config.port());
        assertEquals("0.0.0.0", config.host());
        assertEquals("javawebsocket", config.type());
        assertEquals(0, config.heartbeatSeconds());
        assertTrue(config.reuseAddress());
        assertEquals(0, config.sessionIdleTimeoutSeconds());
        assertEquals(-1, config.maxPendingConnections());
    }

    /**
     * 已配置的服务端键必须覆盖默认值，并落到对应字段。
     */
    @Test
    public void shouldOverrideServerKeys() {
        ServerConfig config = WebSocketConfigLoader.loadServerConfig(prop(
                "websocket.server.port", "8080",
                "websocket.server.type", "javawebsocket",
                "websocket.server.heartbeat-seconds", "15",
                "websocket.server.host", "127.0.0.1",
                "websocket.server.reuse-address", "false",
                "websocket.server.session-idle-timeout-seconds", "120",
                "websocket.server.max-pending-connections", "64"));

        assertEquals(8080, config.port());
        assertEquals("127.0.0.1", config.host());
        assertEquals(15, config.heartbeatSeconds());
        assertFalse(config.reuseAddress());
        assertEquals(120, config.sessionIdleTimeoutSeconds());
        assertEquals(64, config.maxPendingConnections());
    }

    /**
     * 必填键 {@code websocket.server.port} 缺失即抛错，表现为应用启动失败。
     */
    @Test
    public void shouldRejectMissingServerPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> WebSocketConfigLoader.loadServerConfig(prop("websocket.server.host", "127.0.0.1")));
        assertTrue("异常消息应含缺失的键名", exception.getMessage().contains("websocket.server.port"));
    }

    /**
     * 端口越界由 setter 判失败。
     */
    @Test
    public void shouldRejectOutOfRangeServerPort() {
        assertThrows(IllegalArgumentException.class,
                () -> WebSocketConfigLoader.loadServerConfig(prop("websocket.server.port", "70000")));
        assertThrows(IllegalArgumentException.class,
                () -> WebSocketConfigLoader.loadServerConfig(prop("websocket.server.port", "-1")));
    }

    /**
     * 端口取值语法非法按含键名的配置错误上报。
     */
    @Test
    public void shouldRejectNonNumericServerPort() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> WebSocketConfigLoader.loadServerConfig(prop("websocket.server.port", "not-a-port")));
        assertTrue("异常消息应含出错的键名", exception.getMessage().contains("websocket.server.port"));
    }

    /**
     * 由交替出现的键值对构造 {@link Prop}，与仓库内既有测试的构造约定一致。
     *
     * @param keyValues 交替出现的键与值
     * @return 配置对象
     */
    private static Prop prop(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be key/value pairs");
        }
        Prop prop = new Prop();
        for (int i = 0; i < keyValues.length; i += 2) {
            prop.getProperties().setProperty(keyValues[i], keyValues[i + 1]);
        }
        return prop;
    }
}
