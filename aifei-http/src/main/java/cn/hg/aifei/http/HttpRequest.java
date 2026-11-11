/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

import cn.hg.aifei.http.internal.HttpCodec;
import cn.hg.aifei.http.internal.HttpRetry;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一次逻辑请求的<b>可变构建器</b>：方法、URL、query 参数、请求头、请求体、显式资源名、逐请求超时与重试。
 *
 * <p>可变是为让拦截器能在 {@code before} 阶段补鉴权头（见 {@link HttpInterceptor}）；代价是<b>非线程安全</b>，
 * 不应跨线程共享。一次 {@link HttpClient#exec(HttpRequest)} 返回后对象可安全复用。</p>
 *
 * <p>URL 不在此解析：{@link #url()} 只做字符串拼接，资源名推导与建连均在模块内部完成。</p>
 */
public final class HttpRequest {

    /**
     * query 参数起始符。
     */
    private static final String QUERY_PREFIX = "?";

    /**
     * query 参数与路径 / 其它参数的连接符。
     */
    private static final String QUERY_SEPARATOR = "&";

    /**
     * query 参数键值分隔符。
     */
    private static final char QUERY_ASSIGN = '=';

    /**
     * Content-Type 头名。
     */
    private static final String CONTENT_TYPE = "Content-Type";

    private final String method;

    private final String rawUrl;

    /**
     * 已编码的 query 片段，形如 {@code name=value}，按追加顺序保序。
     */
    private final List<String> queryPairs = new ArrayList<>();

    /**
     * 请求头；键保留调用方书写的大小写，值为该键的全部取值。
     */
    private final Map<String, List<String>> headers = new LinkedHashMap<>();

    private String textBody;

    private byte[] binaryBody;

    private String explicitResource;

    private int connectMillis;

    private int readMillis;

    private int retryTimes = -1;

    private long retryIntervalMillis = -1L;

    private HttpRequest(String method, String url) {
        this.method = method;
        this.rawUrl = url;
    }

    /**
     * 构造请求。
     *
     * @param method 请求方法，原样保存并归一为大写
     * @param url    请求 URL，原样保存（不校验合法性）
     * @return 请求构建器
     * @throws IllegalArgumentException {@code method} 或 {@code url} 为空白
     */
    public static HttpRequest of(String method, String url) {
        requireText(method, "method");
        requireText(url, "url");
        return new HttpRequest(method.toUpperCase(Locale.ROOT), url.trim());
    }

    /**
     * 构造 GET 请求。
     *
     * @param url 请求 URL
     * @return 请求构建器
     * @throws IllegalArgumentException {@code url} 为空白
     */
    public static HttpRequest get(String url) {
        return of("GET", url);
    }

    /**
     * 构造 POST 请求。
     *
     * @param url 请求 URL
     * @return 请求构建器
     * @throws IllegalArgumentException {@code url} 为空白
     */
    public static HttpRequest post(String url) {
        return of("POST", url);
    }

    /**
     * 构造 PUT 请求。
     *
     * @param url 请求 URL
     * @return 请求构建器
     * @throws IllegalArgumentException {@code url} 为空白
     */
    public static HttpRequest put(String url) {
        return of("PUT", url);
    }

    /**
     * 构造 DELETE 请求。
     *
     * @param url 请求 URL
     * @return 请求构建器
     * @throws IllegalArgumentException {@code url} 为空白
     */
    public static HttpRequest delete(String url) {
        return of("DELETE", url);
    }

    /**
     * 构造 HEAD 请求。
     *
     * @param url 请求 URL
     * @return 请求构建器
     * @throws IllegalArgumentException {@code url} 为空白
     */
    public static HttpRequest head(String url) {
        return of("HEAD", url);
    }

    /**
     * 追加一个 query 参数；名称与取值均按 UTF-8 编码。
     *
     * @param name  参数名
     * @param value 参数值，{@code null} 视为空串
     * @return 本构建器
     * @throws IllegalArgumentException {@code name} 为空白
     */
    public HttpRequest query(String name, Object value) {
        requireText(name, "query name");
        String encodedValue = value == null ? "" : String.valueOf(value);
        this.queryPairs.add(encode(name) + QUERY_ASSIGN + encode(encodedValue));
        return this;
    }

    /**
     * 覆盖同名请求头（大小写不敏感匹配）。
     *
     * @param name  头名
     * @param value 头值；{@code null} 表示移除该头
     * @return 本构建器
     * @throws IllegalArgumentException {@code name} 为空白
     */
    public HttpRequest header(String name, String value) {
        requireText(name, "header name");
        removeHeader(name);
        if (value != null) {
            List<String> values = new ArrayList<>();
            values.add(value);
            this.headers.put(name, values);
        }
        return this;
    }

    /**
     * 追加一个多值请求头，不覆盖同名头的既有取值。
     *
     * @param name  头名
     * @param value 头值
     * @return 本构建器
     * @throws IllegalArgumentException {@code name} 为空白，或 {@code value} 为 {@code null}
     */
    public HttpRequest addHeader(String name, String value) {
        requireText(name, "header name");
        if (value == null) {
            throw new IllegalArgumentException("header value 不能为 null: " + name);
        }
        List<String> existing = headerEntry(name);
        if (existing == null) {
            List<String> values = new ArrayList<>();
            values.add(value);
            this.headers.put(name, values);
        } else {
            existing.add(value);
        }
        return this;
    }

    /**
     * 设置 {@code Content-Type}，同时决定文本请求体编码与响应体解码所用的 charset。
     *
     * @param contentType 媒体类型
     * @return 本构建器
     * @throws IllegalArgumentException {@code contentType} 为空白
     */
    public HttpRequest contentType(String contentType) {
        requireText(contentType, "contentType");
        return header(CONTENT_TYPE, contentType.trim());
    }

    /**
     * 设置文本请求体；编码推迟到读取 {@link #body()} 时按当时的 {@code Content-Type} charset 执行。
     *
     * @param text 文本体；{@code null} 表示清除请求体
     * @return 本构建器
     */
    public HttpRequest body(String text) {
        this.textBody = text;
        this.binaryBody = null;
        return this;
    }

    /**
     * 设置二进制请求体，入参做防御性拷贝。
     *
     * @param bytes 字节体；{@code null} 表示清除请求体
     * @return 本构建器
     */
    public HttpRequest body(byte[] bytes) {
        this.binaryBody = bytes == null ? null : bytes.clone();
        this.textBody = null;
        return this;
    }

    /**
     * 设置显式资源名，完全替代默认推导。
     *
     * @param resource 资源名
     * @return 本构建器
     * @throws IllegalArgumentException {@code resource} 为空白
     */
    public HttpRequest resource(String resource) {
        requireText(resource, "resource");
        this.explicitResource = resource.trim();
        return this;
    }

    /**
     * 逐请求覆盖建连超时。
     *
     * @param millis 毫秒数
     * @return 本构建器
     * @throws IllegalArgumentException {@code millis} 小于 1
     */
    public HttpRequest connectTimeout(int millis) {
        requirePositive(millis, "connect timeout");
        this.connectMillis = millis;
        return this;
    }

    /**
     * 逐请求覆盖读超时。
     *
     * @param millis 毫秒数
     * @return 本构建器
     * @throws IllegalArgumentException {@code millis} 小于 1
     */
    public HttpRequest readTimeout(int millis) {
        requirePositive(millis, "read timeout");
        this.readMillis = millis;
        return this;
    }

    /**
     * 开启重试，重试间隔继承全局默认。
     *
     * @param times 重试次数
     * @return 本构建器
     * @throws IllegalArgumentException {@code times} 为负数，或方法非幂等
     */
    public HttpRequest retry(int times) {
        if (times < 0) {
            throw new IllegalArgumentException("retry times 不能为负数: " + times);
        }
        HttpRetry.requireIdempotent(this.method);
        this.retryTimes = times;
        return this;
    }

    /**
     * 开启重试并指定固定重试间隔。
     *
     * @param times          重试次数
     * @param intervalMillis 每次重试前的固定等待，{@code -1} 表示继承全局默认
     * @return 本构建器
     * @throws IllegalArgumentException {@code times} 为负数、{@code intervalMillis} 小于 0，或方法非幂等
     */
    public HttpRequest retry(int times, long intervalMillis) {
        if (times < 0) {
            throw new IllegalArgumentException("retry times 不能为负数: " + times);
        }
        if (intervalMillis < 0) {
            throw new IllegalArgumentException("retry interval 不能为负数: " + intervalMillis);
        }
        HttpRetry.requireIdempotent(this.method);
        this.retryTimes = times;
        this.retryIntervalMillis = intervalMillis;
        return this;
    }

    /**
     * 请求方法，大写。
     *
     * @return 方法名
     */
    public String method() {
        return method;
    }

    /**
     * 完整请求 URL（原始 URL 与已编码 query 拼接结果）。
     *
     * @return 完整 URL
     */
    public String url() {
        if (queryPairs.isEmpty()) {
            return rawUrl;
        }
        StringBuilder builder = new StringBuilder(rawUrl);
        builder.append(rawUrl.indexOf('?') >= 0 ? QUERY_SEPARATOR : QUERY_PREFIX);
        for (int i = 0; i < queryPairs.size(); i++) {
            if (i > 0) {
                builder.append(QUERY_SEPARATOR);
            }
            builder.append(queryPairs.get(i));
        }
        return builder.toString();
    }

    /**
     * 显式资源名。
     *
     * @return 显式资源名；未设置时返回 {@code null}
     */
    public String explicitResource() {
        return explicitResource;
    }

    /**
     * 请求头不可变视图，键保留调用方书写的大小写。
     *
     * @return 头名到不可变取值列表的不可变映射
     */
    public Map<String, List<String>> headers() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            snapshot.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * 请求体字节。
     *
     * @return 无请求体时返回 {@code null}；文本体按当前 {@code Content-Type} 的 charset 编码后返回
     */
    public byte[] body() {
        if (binaryBody != null) {
            return binaryBody.clone();
        }
        if (textBody == null) {
            return null;
        }
        return HttpCodec.encode(textBody, firstHeader(CONTENT_TYPE));
    }

    /**
     * 逐请求建连超时。
     *
     * @return 毫秒数；未设置时返回 0（继承全局默认）
     */
    public int connectTimeout() {
        return connectMillis;
    }

    /**
     * 逐请求读超时。
     *
     * @return 毫秒数；未设置时返回 0（继承全局默认）
     */
    public int readTimeout() {
        return readMillis;
    }

    /**
     * 逐请求重试次数。
     *
     * @return 重试次数；未设置时返回 -1（继承全局默认）
     */
    public int retryTimes() {
        return retryTimes;
    }

    /**
     * 逐请求重试间隔。
     *
     * @return 毫秒数；未设置时返回 -1（继承全局默认）
     */
    public long retryIntervalMillis() {
        return retryIntervalMillis;
    }

    /**
     * 大小写不敏感地取首个同名头的取值。
     *
     * @param name 头名
     * @return 首个取值；不存在时返回 {@code null}
     */
    private String firstHeader(String name) {
        List<String> values = headerEntry(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * 大小写不敏感地取同名头的取值列表。
     *
     * @param name 头名
     * @return 取值列表；不存在时返回 {@code null}
     */
    private List<String> headerEntry(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 移除全部同名头（大小写不敏感）。
     *
     * @param name 头名
     */
    private void removeHeader(String name) {
        List<String> obsoleteKeys = new ArrayList<>();
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                obsoleteKeys.add(key);
            }
        }
        for (String key : obsoleteKeys) {
            headers.remove(key);
        }
    }

    /**
     * 按 UTF-8 编码 query 片段。
     *
     * @param text 原文
     * @return 编码结果
     */
    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, HttpCodec.DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new HttpException("请求参数编码失败: " + text, e);
        }
    }

    /**
     * 校验文本参数非空白。
     *
     * @param text  取值
     * @param field 字段名（用于失败消息）
     * @throws IllegalArgumentException {@code text} 为 {@code null} 或空白
     */
    private static void requireText(String text, String field) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    /**
     * 校验正整数参数。
     *
     * @param value 取值
     * @param field 字段名（用于失败消息）
     * @throws IllegalArgumentException {@code value} 小于 1
     */
    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " 必须 >= 1，实际为 " + value);
        }
    }
}
