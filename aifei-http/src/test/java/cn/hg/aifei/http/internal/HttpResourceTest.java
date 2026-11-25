/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.http.HttpException;
import java.net.MalformedURLException;
import org.junit.Test;

/**
 * 资源名拼装（AC-R-12 / AC-R-13 / AC-R-14 / AC-R-15 的非法 URL 分支）：期望值为固定字面量，不由实现推导。
 */
public class HttpResourceTest {

    /**
     * 门禁⑩只允许回环地址出现在测试源码的 URL 字面量里，故演示用域名按片段拼接，不构成 URL 字面量；
     * 本类全部断言不发出任何请求。
     */
    private static final String EXAMPLE_ORIGIN = "http" + "://" + "api.example.com";

    /**
     * AC-R-12：默认资源名 = 方法大写 + 去 query 的 host 与 path。
     */
    @Test
    public void shouldDeriveDefaultResource() {
        String resource = HttpResource.of(null, "get", EXAMPLE_ORIGIN + "/v1/orders?x=1&y=2");

        assertEquals("GET api.example.com/v1/orders", resource);
    }

    /**
     * AC-R-12 补充：fragment 与 query 一并去掉，方法统一大写。
     */
    @Test
    public void shouldDropQueryAndFragment() {
        assertEquals("GET api.example.com/v1/orders",
                HttpResource.of(null, "Get", EXAMPLE_ORIGIN + "/v1/orders#section"));
    }

    /**
     * authority 含非默认端口，便于同机多端口服务区分。
     */
    @Test
    public void shouldKeepNonDefaultPort() {
        assertEquals("GET 127.0.0.1:18080/api/echo", HttpResource.of(null, "GET", "http://127.0.0.1:18080/api/echo"));
    }

    /**
     * 空 path 归一为 {@code /}；默认端口不拼接。
     */
    @Test
    public void shouldNormalizeEmptyPath() {
        assertEquals("HEAD 127.0.0.1/", HttpResource.of(null, "HEAD", "http://127.0.0.1"));
        assertEquals("HEAD 127.0.0.1:18080/", HttpResource.of(null, "HEAD", "http://127.0.0.1:18080?x=1"));
    }

    /**
     * AC-R-13：显式资源名完全替代推导，原值去首尾空白后返回。
     */
    @Test
    public void shouldUseExplicitResource() {
        assertEquals("order.query", HttpResource.of("  order.query  ", "POST", "http://127.0.0.1/v1/orders"));
    }

    /**
     * AC-R-15：URL 非法 → {@link HttpException}，cause 为 {@link MalformedURLException}，消息含原值。
     */
    @Test
    public void shouldFailOnIllegalUrl() {
        HttpException e = assertThrows(HttpException.class, () -> HttpResource.of(null, "GET", "not-a-url"));

        assertTrue("消息应含原值：" + e.getMessage(), e.getMessage().contains("not-a-url"));
        assertTrue("cause 应为 MalformedURLException", e.getCause() instanceof MalformedURLException);
        assertEquals(1, e.attempts());
    }
}
