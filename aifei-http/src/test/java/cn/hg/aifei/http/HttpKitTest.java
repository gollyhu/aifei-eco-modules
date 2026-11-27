/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import cn.hg.aifei.http.internal.HttpHolder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 静态门面：未装配即失败、取到的实例与注入一致、五个便捷方法只做转发。
 */
public class HttpKitTest {

    /**
     * 门面转发用例使用的回环地址（被伪客户端拦截，不发真实请求）。
     */
    private static final String LOOPBACK_URL = "http://127.0.0.1:18080/api/echo";

    /**
     * 每个用例前清空门面实例。
     */
    @Before
    public void clearHolder() {
        HttpHolder.clear();
    }

    /**
     * 每个用例后清空门面实例。
     */
    @After
    public void restoreHolder() {
        HttpHolder.clear();
    }

    /**
     * 未装配时取用失败，不静默返回 {@code null}。
     */
    @Test
    public void shouldFailBeforeAssembly() {
        HttpException e = assertThrows(HttpException.class, HttpKit::client);

        assertEquals("HTTP 客户端未装配或已停止", e.getMessage());
        assertThrows(HttpException.class, () -> HttpKit.get(LOOPBACK_URL));
        assertThrows(HttpException.class, () -> HttpKit.post(LOOPBACK_URL, "x"));
    }

    /**
     * 装配后可取到同一实例，{@code exec} 直接转发。
     */
    @Test
    public void shouldExposeInstalledClient() {
        RecordingClient client = new RecordingClient();
        HttpHolder.install(client);

        assertSame(client, HttpKit.client());

        HttpRequest request = HttpRequest.get(LOOPBACK_URL).resource("demo");
        HttpResponse response = HttpKit.exec(request);

        assertEquals(200, response.statusCode());
        assertEquals(Collections.singletonList(request), client.requests);
    }

    /**
     * 五个便捷方法只做转发：方法名与文本体按约定组装。
     */
    @Test
    public void shouldForwardConvenienceMethods() {
        RecordingClient client = new RecordingClient();
        HttpHolder.install(client);

        HttpKit.get(LOOPBACK_URL);
        HttpKit.post(LOOPBACK_URL, "甲");
        HttpKit.put(LOOPBACK_URL, "乙");
        HttpKit.delete(LOOPBACK_URL);
        HttpKit.head(LOOPBACK_URL);

        assertEquals(5, client.requests.size());
        assertEquals("GET", client.requests.get(0).method());
        assertEquals("POST", client.requests.get(1).method());
        assertEquals("乙", text(client.requests.get(2).body()));
        assertEquals("DELETE", client.requests.get(3).method());
        assertEquals("HEAD", client.requests.get(4).method());
        assertEquals(LOOPBACK_URL, client.requests.get(1).url());
    }

    /**
     * 清除后取用立即失败。
     */
    @Test
    public void shouldFailAfterClear() {
        HttpHolder.install(new RecordingClient());

        HttpHolder.clear();

        assertThrows(HttpException.class, HttpKit::client);
    }

    /**
     * 以 UTF-8 还原请求体文本。
     *
     * @param body 字节体
     * @return 文本
     */
    private static String text(byte[] body) {
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 录制型伪客户端：只记录收到的请求，固定返回 200。
     */
    private static final class RecordingClient implements HttpClient {

        private final List<HttpRequest> requests = new ArrayList<>();

        @Override
        public HttpResponse exec(HttpRequest request) {
            requests.add(request);
            return HttpResponse.of(200, null, "ok".getBytes(StandardCharsets.UTF_8), 0L, 1);
        }
    }
}
