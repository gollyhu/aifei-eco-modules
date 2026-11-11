/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.echo;

import cn.aifei.core.Handler;
import cn.aifei.demo.common.arch.In;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.json.Json;
import cn.aifei.util.StrUtil;
import cn.hg.aifei.http.demo.common.DemoAppConfig;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * 可控回环目标端点 {@code /demo/echo}：给 aifei-http 的演示端点提供一个「自己请求自己」的真实服务端。
 *
 * <p>整条链路零外网：请求目标恒为 {@code http://127.0.0.1:18120} 本机自身。查询参数决定服务端行为：</p>
 *
 * <table border="1">
 *   <caption>可控行为</caption>
 *   <tr><th>参数</th><th>含义</th><th>默认</th></tr>
 *   <tr><td>{@code status}</td><td>指定响应状态码（100–599）</td><td>200</td></tr>
 *   <tr><td>{@code delay}</td><td>应答前休眠毫秒数（0–30000），用于触发读超时</td><td>0</td></tr>
 *   <tr><td>{@code failFirst}</td><td>同一 {@code key} 的前 N 次返回 503，第 N+1 次起按 {@code status} 返回</td><td>0</td></tr>
 *   <tr><td>{@code key}</td><td>{@code failFirst} 的计数分组名</td><td>{@code default}</td></tr>
 *   <tr><td>{@code gzip}</td><td>响应体 gzip 压缩并带 {@code Content-Encoding: gzip}</td><td>false</td></tr>
 *   <tr><td>{@code redirect}</td><td>返回 302 且 {@code Location} 指向本端点自身</td><td>false</td></tr>
 *   <tr><td>{@code text}</td><td>用给定文本作为响应体（默认回显 JSON）</td><td>无</td></tr>
 * </table>
 *
 * <p>本 Handler 必须排在 {@link cn.aifei.demo.common.arch.IoHandler} 之前：命中 {@link #ECHO_PATH} 时自己
 * 写响应（状态码 / 头 / 原始字节都在自己手里，才能演示 5xx、gzip、302），其余路径一律交棒给 {@code next}。</p>
 */
public class EchoHandler extends Handler<In, Out> {

    /**
     * 本 Handler 接管的请求路径。
     */
    public static final String ECHO_PATH = DemoAppConfig.ECHO_PATH;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

    private static final String CONTENT_TYPE_TEXT = "text/plain;charset=UTF-8";

    /**
     * 请求体回显上限：超过即截断回显（只影响回显内容，不影响 demo 的任何断言）。
     */
    private static final int MAX_REQUEST_BODY_BYTES = 64 * 1024;

    /**
     * 延迟上限（毫秒），防止页面误传把工作线程挂死。
     */
    private static final long MAX_DELAY_MILLIS = 30000L;

    private static final int MAX_FAIL_FIRST = 1000;

    private static final HttpString X_ECHO_METHOD = new HttpString("X-Echo-Method");

    private static final HttpString X_ECHO_ATTEMPT = new HttpString("X-Echo-Attempt");

    private static final HttpString X_ECHO_STATUS = new HttpString("X-Echo-Status");

    private static final HttpString X_ECHO_FAIL_FIRST = new HttpString("X-Echo-Fail-First");

    private static final HttpString X_ECHO_KEY = new HttpString("X-Echo-Key");

    private static final HttpString X_ECHO_BODY_SIZE = new HttpString("X-Echo-Body-Size");

    private static final HttpString X_ECHO_ACCEPT_ENCODING = new HttpString("X-Echo-Accept-Encoding");

    /**
     * 处理一次请求：命中 {@link #ECHO_PATH} 时按参数应答，否则交给责任链下游。
     *
     * @param path 请求路径
     * @param in 应用侧输入（含 Undertow exchange）
     * @param out 应用侧输出，本 Handler 自己写响应，不使用它
     * @throws Exception 写响应失败时抛出，由 IoDispatcher 记录并结束交换
     */
    @Override
    public void handle(String path, In in, Out out) throws Exception {
        if (!ECHO_PATH.equals(path)) {
            if (next != null) {
                next.handle(path, in, out);
            }
            return;
        }
        HttpServerExchange exchange = in.getExchange();
        Map<String, String> params = queryParams(exchange);
        int status = intParam(params, "status", 200, 100, 599);
        long delay = longParam(params, "delay", 0L, 0L, MAX_DELAY_MILLIS);
        int failFirst = intParam(params, "failFirst", 0, 0, MAX_FAIL_FIRST);
        String key = StrUtil.isBlank(params.get("key")) ? EchoState.DEFAULT_KEY : params.get("key").trim();
        boolean gzip = boolParam(params, "gzip");
        boolean redirect = boolParam(params, "redirect");
        String text = params.get("text");

        byte[] requestBody = readRequestBody(exchange);
        int attemptNo = EchoState.hit(key, failFirst);

        String location = null;
        if (redirect) {
            // 指回自身且不带 failFirst / gzip，即便客户端跟随也只会拿到一次干净 200
            status = 302;
            location = DemoAppConfig.echoUrl() + "?redirected=1&status=200";
        } else if (failFirst > 0 && attemptNo <= failFirst) {
            status = 503;
        }
        if (delay > 0L) {
            sleepQuietly(delay);
        }

        byte[] payload;
        String contentType;
        if (redirect) {
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("redirect", true);
            body.put("status", status);
            body.put("location", location);
            body.put("attemptNo", attemptNo);
            body.put("note", "302 由本端点返回；aifei-http 固定不跟随重定向，3xx 原样返回");
            payload = toJson(body);
            contentType = CONTENT_TYPE_JSON;
        } else if (text != null) {
            payload = text.getBytes(UTF8);
            contentType = CONTENT_TYPE_TEXT;
        } else {
            payload = toJson(echoBody(exchange, params, requestBody, attemptNo, failFirst, status, key));
            contentType = CONTENT_TYPE_JSON;
        }
        if (gzip) {
            payload = gzip(payload);
        }

        HeaderMap responseHeaders = exchange.getResponseHeaders();
        responseHeaders.put(Headers.CONTENT_TYPE, contentType);
        responseHeaders.put(X_ECHO_METHOD, exchange.getRequestMethod().toString());
        responseHeaders.put(X_ECHO_ATTEMPT, String.valueOf(attemptNo));
        responseHeaders.put(X_ECHO_STATUS, String.valueOf(status));
        responseHeaders.put(X_ECHO_FAIL_FIRST, String.valueOf(failFirst));
        responseHeaders.put(X_ECHO_KEY, key);
        responseHeaders.put(X_ECHO_BODY_SIZE, String.valueOf(requestBody.length));
        String acceptEncoding = exchange.getRequestHeaders().getFirst(Headers.ACCEPT_ENCODING);
        responseHeaders.put(X_ECHO_ACCEPT_ENCODING, acceptEncoding == null ? "null" : acceptEncoding);
        if (gzip) {
            responseHeaders.put(Headers.CONTENT_ENCODING, "gzip");
        }
        if (location != null) {
            responseHeaders.put(Headers.LOCATION, location);
        }
        exchange.setStatusCode(status);

        // 204 / 304 按 HTTP 语义不带响应体
        if (status == 204 || status == 304) {
            exchange.endExchange();
            return;
        }

        responseHeaders.put(Headers.CONTENT_LENGTH, String.valueOf(payload.length));
        OutputStream outputStream = exchange.getOutputStream();
        outputStream.write(payload);
        outputStream.flush();
        outputStream.close();
    }

    /**
     * 组装回显 JSON：方法、路径、查询串、请求头、请求体大小与内容，以及本次尝试序号。
     */
    private Map<String, Object> echoBody(HttpServerExchange exchange, Map<String, String> params,
                                         byte[] requestBody, int attemptNo, int failFirst, int status, String key) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("echo", true);
        body.put("method", exchange.getRequestMethod().toString());
        body.put("path", exchange.getRequestPath());
        body.put("queryString", exchange.getQueryString());
        body.put("params", new LinkedHashMap<String, Object>(params));
        body.put("headers", requestHeaders(exchange));
        body.put("bodySize", requestBody.length);
        body.put("body", requestBody.length == 0 ? "" : new String(requestBody, UTF8));
        body.put("attemptNo", attemptNo);
        body.put("failFirst", failFirst);
        body.put("status", status);
        body.put("key", key);
        body.put("note", "本端点由 demo 自身 HIO 实现，为 aifei-http 提供可控回环目标");
        return body;
    }

    /**
     * 请求头全量（保持原始大小写与多值）。
     */
    private Map<String, Object> requestHeaders(HttpServerExchange exchange) {
        Map<String, Object> headers = new LinkedHashMap<String, Object>();
        HeaderMap headerMap = exchange.getRequestHeaders();
        for (HttpString name : headerMap.getHeaderNames()) {
            headers.put(name.toString(), new ArrayList<String>(headerMap.get(name)));
        }
        return headers;
    }

    /**
     * 查询参数取每项首个值（本端点只消费单值参数）。
     */
    private Map<String, String> queryParams(HttpServerExchange exchange) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Deque<String>> e : exchange.getQueryParameters().entrySet()) {
            Deque<String> values = e.getValue();
            if (values != null && !values.isEmpty()) {
                params.put(e.getKey(), values.peekFirst());
            }
        }
        return params;
    }

    /**
     * 读取请求体：只在声明了正的 {@code Content-Length} 时读取，避免无体请求被读阻塞。
     */
    private byte[] readRequestBody(HttpServerExchange exchange) throws IOException {
        String contentLength = exchange.getRequestHeaders().getFirst(Headers.CONTENT_LENGTH);
        if (StrUtil.isBlank(contentLength)) {
            return new byte[0];
        }
        long length;
        try {
            length = Long.parseLong(contentLength.trim());
        } catch (NumberFormatException e) {
            return new byte[0];
        }
        if (length <= 0L) {
            return new byte[0];
        }
        int limit = (int) Math.min(length, MAX_REQUEST_BODY_BYTES);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(limit);
        InputStream inputStream = exchange.getInputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int read;
        while (total < limit && (read = inputStream.read(chunk, 0, Math.min(chunk.length, limit - total))) != -1) {
            buffer.write(chunk, 0, read);
            total += read;
        }
        return buffer.toByteArray();
    }

    private byte[] toJson(Object body) {
        return Json.of(body).toJson().getBytes(UTF8);
    }

    private byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(raw.length);
        GZIPOutputStream gzipOutputStream = new GZIPOutputStream(buffer);
        try {
            gzipOutputStream.write(raw);
            gzipOutputStream.finish();
        } finally {
            gzipOutputStream.close();
        }
        return buffer.toByteArray();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int intParam(Map<String, String> params, String name, int defaultValue, int min, int max) {
        long value = longParam(params, name, defaultValue, min, max);
        return (int) value;
    }

    private long longParam(Map<String, String> params, String name, long defaultValue, long min, long max) {
        String raw = params.get(name);
        if (StrUtil.isBlank(raw)) {
            return defaultValue;
        }
        long value;
        try {
            value = Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private boolean boolParam(Map<String, String> params, String name) {
        String raw = params.get(name);
        if (StrUtil.isBlank(raw)) {
            return false;
        }
        String v = raw.trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

}
