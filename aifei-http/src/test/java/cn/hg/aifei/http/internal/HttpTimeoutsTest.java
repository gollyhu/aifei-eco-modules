/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.junit.Test;

/**
 * 超时装配（AC-R-09）：逐请求值优先，未设置（{@code <= 0}）时取全局默认。
 */
public class HttpTimeoutsTest {

    /**
     * 逐请求值优先于全局默认。
     */
    @Test
    public void shouldPreferRequestValues() {
        HttpTimeouts timeouts = HttpTimeouts.resolve(120, 340, config("5000", "10000"));

        assertEquals(120, timeouts.connectMillis());
        assertEquals(340, timeouts.readMillis());
    }

    /**
     * 逐请求未设置时继承全局默认。
     */
    @Test
    public void shouldFallBackToConfig() {
        HttpTimeouts timeouts = HttpTimeouts.resolve(0, 0, config("1500", "2500"));

        assertEquals(1500, timeouts.connectMillis());
        assertEquals(2500, timeouts.readMillis());
    }

    /**
     * 两段超时相互独立：只覆盖一段时另一段仍取全局默认。
     */
    @Test
    public void shouldResolveSegmentsIndependently() {
        HttpTimeouts timeouts = HttpTimeouts.resolve(0, 100, config("1500", "2500"));

        assertEquals(1500, timeouts.connectMillis());
        assertEquals(100, timeouts.readMillis());
    }

    /**
     * 配置缺失时按内置默认值。
     */
    @Test
    public void shouldUseBuiltInDefaultsWhenConfigNull() {
        HttpTimeouts timeouts = HttpTimeouts.resolve(0, 0, null);

        assertEquals(HttpConfig.DEFAULT_CONNECT_TIMEOUT, timeouts.connectMillis());
        assertEquals(HttpConfig.DEFAULT_READ_TIMEOUT, timeouts.readMillis());
    }

    /**
     * 构造配置。
     *
     * @param connectMillis 建连超时
     * @param readMillis    读超时
     * @return 配置域
     */
    private static HttpConfig config(String connectMillis, String readMillis) {
        Properties properties = new Properties();
        properties.setProperty(HttpConfig.KEY_CONNECT_TIMEOUT, connectMillis);
        properties.setProperty(HttpConfig.KEY_READ_TIMEOUT, readMillis);
        return HttpConfig.parse(properties);
    }
}
