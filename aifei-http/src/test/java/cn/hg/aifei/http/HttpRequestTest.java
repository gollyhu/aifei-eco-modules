/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 请求构建器（含拒绝路径与编码规则）：纯对象行为，不联网。
 */
public class HttpRequestTest {

    /**
     * 用例使用的回环地址（不发真实请求）。
     */
    private static final String BASE_URL = "http://127.0.0.1:18080/api/echo";

    /**
     * 方法名归一为大写。
     */
    @Test
    public void shouldNormalizeMethodCase() {
        assertEquals("GET", HttpRequest.of("get", BASE_URL).method());
        assertEquals("POST", HttpRequest.post(BASE_URL).method());
        assertEquals("PUT", HttpRequest.put(BASE_URL).method());
        assertEquals("DELETE", HttpRequest.delete(BASE_URL).method());
        assertEquals("HEAD", HttpRequest.head(BASE_URL).method());
    }

    /**
     * 方法与 URL 为空白属调用方写错。
     */
    @Test
    public void shouldRejectBlankMethodAndUrl() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.of("  ", BASE_URL));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.of("GET", " "));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(null));
    }

    /**
     * query 按 UTF-8 编码，多个参数用 {@code &} 连接。
     *
     * @throws Exception 编码异常
     */
    @Test
    public void shouldAppendEncodedQuery() throws Exception {
        String value = "中文 空格&x";
        HttpRequest request = HttpRequest.get(BASE_URL).query("q", value).query("page", 2);

        assertEquals(BASE_URL + "?q=" + URLEncoder.encode(value, "UTF-8") + "&page=2", request.url());
    }

    /**
     * URL 已有 query 时用 {@code &} 续接。
     */
    @Test
    public void shouldJoinQueryWithExistingQuery() {
        assertEquals(BASE_URL + "?x=1&y=2", HttpRequest.get(BASE_URL + "?x=1").query("y", 2).url());
    }

    /**
     * {@code null} 取值按空串编码；空白参数名属调用方写错。
     */
    @Test
    public void shouldHandleNullQueryValue() {
        assertEquals(BASE_URL + "?q=", HttpRequest.get(BASE_URL).query("q", null).url());
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).query(" ", "v"));
    }

    /**
     * {@code header} 覆盖同名头（大小写不敏感），{@code addHeader} 追加多值。
     */
    @Test
    public void shouldOverrideAndAppendHeaders() {
        HttpRequest request = HttpRequest.get(BASE_URL)
                .header("Content-Type", "text/plain")
                .header("content-type", "application/json")
                .addHeader("Set-Cookie", "a=1")
                .addHeader("Set-Cookie", "b=2");

        Map<String, List<String>> headers = request.headers();
        assertEquals(2, headers.size());
        assertEquals("调用方最后一次书写的大小写", "content-type", firstKeyEndingWith(headers, "content-type"));
        assertEquals("application/json", headers.get("content-type").get(0));
        assertEquals(2, headers.get("Set-Cookie").size());
    }

    /**
     * 头值为 {@code null} 等价移除该头。
     */
    @Test
    public void shouldRemoveHeaderOnNullValue() {
        HttpRequest request = HttpRequest.get(BASE_URL).header("X-Token", "t").header("x-token", null);

        assertTrue(request.headers().isEmpty());
    }

    /**
     * 头名为空白属调用方写错；{@code addHeader} 不接受 {@code null} 值。
     */
    @Test
    public void shouldRejectInvalidHeaderArguments() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).header("", "v"));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).addHeader("X", null));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).contentType(" "));
    }

    /**
     * 文本体按当前 {@code Content-Type} 的 charset 编码（延迟到读取时）。
     */
    @Test
    public void shouldEncodeTextBodyWithContentTypeCharset() {
        HttpRequest request = HttpRequest.post(BASE_URL).body("中文");

        assertArrayEquals("中文".getBytes(Charset.forName("UTF-8")), request.body());

        request.contentType("text/plain; charset=GBK");

        assertArrayEquals("中文".getBytes(Charset.forName("GBK")), request.body());
    }

    /**
     * 请求体可被清除；{@code null} 表示无体。
     */
    @Test
    public void shouldClearBody() {
        HttpRequest request = HttpRequest.post(BASE_URL).body("x");
        assertArrayEquals("x".getBytes(Charset.forName("UTF-8")), request.body());

        request.body((String) null);

        assertNull(request.body());
    }

    /**
     * 二进制体入参与返回值都做防御性拷贝。
     */
    @Test
    public void shouldCopyBinaryBody() {
        byte[] source = new byte[]{1, 2, 3};
        HttpRequest request = HttpRequest.post(BASE_URL).body(source);

        source[0] = 9;
        byte[] first = request.body();
        assertEquals(1, first[0]);

        first[0] = 8;
        assertEquals(1, request.body()[0]);
    }

    /**
     * 未设置的逐请求超时与重试返回约定值（0 / -1）。
     */
    @Test
    public void shouldReportUnsetOverrides() {
        HttpRequest request = HttpRequest.get(BASE_URL);

        assertEquals(0, request.connectTimeout());
        assertEquals(0, request.readTimeout());
        assertEquals(-1, request.retryTimes());
        assertEquals(-1L, request.retryIntervalMillis());
        assertNull(request.explicitResource());
    }

    /**
     * 逐请求超时与重试可覆盖；间隔未指定时仍为 -1。
     */
    @Test
    public void shouldRecordOverrides() {
        HttpRequest request = HttpRequest.get(BASE_URL)
                .connectTimeout(100)
                .readTimeout(200)
                .retry(2);

        assertEquals(100, request.connectTimeout());
        assertEquals(200, request.readTimeout());
        assertEquals(2, request.retryTimes());
        assertEquals(-1L, request.retryIntervalMillis());

        request.retry(1, 50L);

        assertEquals(1, request.retryTimes());
        assertEquals(50L, request.retryIntervalMillis());
    }

    /**
     * 超时与重试的负数取值属调用方写错。
     */
    @Test
    public void shouldRejectNegativeOverrides() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).connectTimeout(0));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).readTimeout(-1));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).retry(-1));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).retry(1, -2L));
    }

    /**
     * 非幂等方法无法开启重试，当场拒绝而非静默忽略。
     */
    @Test
    public void shouldRejectRetryForNonIdempotentMethod() {
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.post(BASE_URL).retry(1));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.put(BASE_URL).retry(1));
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.delete(BASE_URL).retry(1));
    }

    /**
     * 幂等方法（大小写不敏感）可开启重试。
     */
    @Test
    public void shouldAllowRetryForIdempotentMethod() {
        assertEquals(1, HttpRequest.of("get", BASE_URL).retry(1).retryTimes());
        assertEquals(3, HttpRequest.head(BASE_URL).retry(3).retryTimes());
    }

    /**
     * 显式资源名可设、可读；空白属调用方写错。
     */
    @Test
    public void shouldRecordExplicitResource() {
        assertEquals("order.query", HttpRequest.get(BASE_URL).resource(" order.query ").explicitResource());
        assertThrows(IllegalArgumentException.class, () -> HttpRequest.get(BASE_URL).resource("  "));
    }

    /**
     * 取首个大小写匹配的键名，用于断言键保留调用方书写的大小写。
     *
     * @param headers 头映射
     * @param expected 期望的键名（小写）
     * @return 实际键名
     */
    private static String firstKeyEndingWith(Map<String, List<String>> headers, String expected) {
        for (String key : headers.keySet()) {
            if (key.toLowerCase(java.util.Locale.ROOT).equals(expected)) {
                return key;
            }
        }
        return null;
    }
}
