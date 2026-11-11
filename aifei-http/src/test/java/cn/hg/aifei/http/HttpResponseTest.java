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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * 响应值对象（AC-R-21 的解码与 AC-R-22 的可读性）：不可变、防御性拷贝、头查找大小写不敏感。
 */
public class HttpResponseTest {

    /**
     * 响应头深拷贝与体 clone：外部改动不影响快照。
     */
    @Test
    public void shouldCopyInputsDefensively() {
        List<String> cookies = new ArrayList<>();
        cookies.add("a=1");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Set-Cookie", cookies);
        byte[] body = new byte[]{1, 2, 3};

        HttpResponse response = HttpResponse.of(200, headers, body, 5L, 1);
        cookies.add("b=2");
        body[0] = 9;
        headers.put("X-Late", Collections.singletonList("x"));

        assertEquals(Collections.singletonList("a=1"), response.headers("Set-Cookie"));
        assertEquals(1, response.bodyAsBytes()[0]);
        assertNull(response.header("X-Late"));
    }

    /**
     * {@code headers()} 与 {@code bodyAsBytes()} 为不可变 / 拷贝视图。
     */
    @Test
    public void shouldExposeUnmodifiableViews() {
        HttpResponse response = HttpResponse.of(200, new LinkedHashMap<String, List<String>>(),
                new byte[]{1}, 0L, 1);

        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("x", null));

        byte[] bytes = response.bodyAsBytes();
        bytes[0] = 9;
        assertEquals(1, response.bodyAsBytes()[0]);
    }

    /**
     * AC-R-22：头查找大小写不敏感，多值可枚举；缺失时返回 {@code null} / 空列表。
     */
    @Test
    public void shouldLookUpHeadersCaseInsensitively() {
        List<String> cookies = new ArrayList<>();
        cookies.add("a=1");
        cookies.add("b=2");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Collections.singletonList("text/plain; charset=UTF-8"));
        headers.put("Set-Cookie", cookies);

        HttpResponse response = HttpResponse.of(200, headers, new byte[0], 0L, 1);

        assertEquals("text/plain; charset=UTF-8", response.header("content-type"));
        assertEquals(2, response.headers("SET-COOKIE").size());
        assertEquals(Collections.singletonList("b=2"), response.headers("set-cookie").subList(1, 2));
        assertNull(response.header("X-Missing"));
        assertTrue(response.headers("X-Missing").isEmpty());
        assertNull(response.header(null));
    }

    /**
     * AC-R-21：响应体按响应头 charset 解码（GBK 与 UTF-8 结果不同）。
     */
    @Test
    public void shouldDecodeBodyByResponseCharset() {
        byte[] gbk = "中文响应".getBytes(Charset.forName("GBK"));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Collections.singletonList("text/plain; charset=GBK"));

        HttpResponse response = HttpResponse.of(200, headers, gbk, 0L, 1);

        assertEquals("中文响应", response.bodyAsString());
        assertArrayEquals(gbk, response.bodyAsBytes());
    }

    /**
     * charset 不可识别 → {@link HttpException}，不静默返回半截字符串。
     */
    @Test
    public void shouldFailOnUnknownCharset() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Collections.singletonList("text/plain; charset=x-unknown"));

        HttpResponse response = HttpResponse.of(200, headers, new byte[]{1}, 0L, 1);

        assertThrows(HttpException.class, response::bodyAsString);
    }

    /**
     * {@code null} 键（状态行）丢弃；{@code null} 体视为空体。
     */
    @Test
    public void shouldDropNullHeaderKeyAndNullBody() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(null, Collections.singletonList("HTTP/1.1 200 OK"));
        headers.put("X-A", null);

        HttpResponse response = HttpResponse.of(200, headers, null, 0L, 1);

        assertEquals(1, response.headers().size());
        assertTrue(response.headers("X-A").isEmpty());
        assertEquals(0, response.bodyAsBytes().length);
        assertEquals("", response.bodyAsString());
    }

    /**
     * 状态码 / 耗时 / 尝试次数可读。
     */
    @Test
    public void shouldExposeMetrics() {
        HttpResponse response = HttpResponse.of(503, null, new byte[0], 12L, 3);

        assertEquals(503, response.statusCode());
        assertEquals(12L, response.elapsedMillis());
        assertEquals(3, response.attempts());
    }

    /**
     * 度量非法属模块装配错误。
     */
    @Test
    public void shouldRejectInvalidMetrics() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpResponse.of(200, null, new byte[0], 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> HttpResponse.of(200, null, new byte[0], -1L, 1));
    }
}
