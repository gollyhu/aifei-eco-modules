/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.http.HttpException;
import java.util.Properties;
import org.junit.Test;

/**
 * 配置域：默认值、非法值即启动失败、消息含键名与原值。
 */
public class HttpConfigTest {

    /**
     * 七个键全部缺失时取默认值。
     */
    @Test
    public void shouldUseDefaultsWhenAbsent() {
        HttpConfig config = HttpConfig.parse(new Properties());

        assertEquals(5000, config.connectTimeout());
        assertEquals(10000, config.readTimeout());
        assertEquals(0, config.retryTimes());
        assertEquals(200L, config.retryInterval());
        assertEquals(8 * 1024 * 1024, config.maxResponseBytes());
        assertFalse(config.gzip());
        assertEquals("aifei-http/1.0.0", config.userAgent());
    }

    /**
     * 配置源为 {@code null} 等价全默认。
     */
    @Test
    public void shouldUseDefaultsWhenPropertiesNull() {
        assertEquals(5000, HttpConfig.parse(null).connectTimeout());
    }

    /**
     * 全部键都可显式配置。
     */
    @Test
    public void shouldParseAllKeys() {
        Properties properties = new Properties();
        properties.setProperty(HttpConfig.KEY_CONNECT_TIMEOUT, "1500");
        properties.setProperty(HttpConfig.KEY_READ_TIMEOUT, "2500");
        properties.setProperty(HttpConfig.KEY_RETRY_TIMES, "2");
        properties.setProperty(HttpConfig.KEY_RETRY_INTERVAL, "0");
        properties.setProperty(HttpConfig.KEY_MAX_RESPONSE_BYTES, "1024");
        properties.setProperty(HttpConfig.KEY_GZIP, "TRUE");
        properties.setProperty(HttpConfig.KEY_USER_AGENT, " demo-agent ");

        HttpConfig config = HttpConfig.parse(properties);

        assertEquals(1500, config.connectTimeout());
        assertEquals(2500, config.readTimeout());
        assertEquals(2, config.retryTimes());
        assertEquals(0L, config.retryInterval());
        assertEquals(1024, config.maxResponseBytes());
        assertTrue(config.gzip());
        assertEquals("demo-agent", config.userAgent());
    }

    /**
     * 空白 User-Agent 合法：表示模块不主动设置该头。
     */
    @Test
    public void shouldAcceptBlankUserAgent() {
        Properties properties = new Properties();
        properties.setProperty(HttpConfig.KEY_USER_AGENT, "   ");

        assertEquals("", HttpConfig.parse(properties).userAgent());
    }

    /**
     * 超时为 0 启动失败，消息含键名与原值。
     */
    @Test
    public void shouldRejectZeroConnectTimeout() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_CONNECT_TIMEOUT, "0")));

        assertTrue("消息应含键名：" + e.getMessage(), e.getMessage().contains("http.connect-timeout"));
        assertTrue("消息应含原值：" + e.getMessage(), e.getMessage().contains("0"));
    }

    /**
     * 超时为负数启动失败。
     */
    @Test
    public void shouldRejectNegativeReadTimeout() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_READ_TIMEOUT, "-1")));

        assertTrue(e.getMessage().contains("http.read-timeout"));
        assertTrue(e.getMessage().contains("-1"));
    }

    /**
     * 重试次数为负启动失败。
     */
    @Test
    public void shouldRejectNegativeRetryTimes() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_RETRY_TIMES, "-2")));

        assertTrue(e.getMessage().contains("http.retry-times"));
        assertTrue(e.getMessage().contains("-2"));
    }

    /**
     * 响应体上限为 0 启动失败。
     */
    @Test
    public void shouldRejectZeroMaxResponseBytes() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_MAX_RESPONSE_BYTES, "0")));

        assertTrue(e.getMessage().contains("http.max-response-bytes"));
    }

    /**
     * 重试间隔为负启动失败；{@code 0} 合法（测试用，无等待）。
     */
    @Test
    public void shouldRejectNegativeRetryInterval() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_RETRY_INTERVAL, "-5")));

        assertTrue(e.getMessage().contains("http.retry-interval"));
        assertEquals(0L, HttpConfig.parse(properties(HttpConfig.KEY_RETRY_INTERVAL, "0")).retryInterval());
    }

    /**
     * 非整数取值启动失败。
     */
    @Test
    public void shouldRejectNonNumericValue() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_RETRY_TIMES, "two")));

        assertTrue(e.getMessage().contains("http.retry-times"));
        assertTrue(e.getMessage().contains("two"));
    }

    /**
     * 非布尔取值启动失败。
     */
    @Test
    public void shouldRejectNonBooleanGzip() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpConfig.parse(properties(HttpConfig.KEY_GZIP, "yes")));

        assertTrue(e.getMessage().contains("http.gzip"));
        assertTrue(e.getMessage().contains("yes"));
    }

    /**
     * 构造只含单个键的配置源。
     *
     * @param key   键名
     * @param value 原值
     * @return 配置源
     */
    private static Properties properties(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }
}
