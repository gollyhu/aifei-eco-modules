/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http;

import cn.hg.aifei.http.internal.HttpCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次调用的<b>不可变结果快照</b>：状态码、响应头、响应体、总耗时、总尝试次数。
 *
 * <p>非 2xx 不抛异常：3xx / 4xx / 5xx 一律正常返回，由业务读 {@link #statusCode()} 判断。</p>
 *
 * <p>构造入口只有 {@link #of(int, Map, byte[], long, int)}，供模块装配使用；业务侧不应手工构造。</p>
 */
public final class HttpResponse {

    /**
     * Content-Type 头名。
     */
    private static final String CONTENT_TYPE = "Content-Type";

    private final int statusCode;

    /**
     * 响应头；键保留响应原样的大小写，值为该键的全部取值。
     */
    private final Map<String, List<String>> headers;

    private final byte[] body;

    private final long elapsedMillis;

    private final int attempts;

    private HttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body,
                         long elapsedMillis, int attempts) {
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.elapsedMillis = elapsedMillis;
        this.attempts = attempts;
    }

    /**
     * 装配响应快照；入参一律防御性拷贝。
     *
     * @param statusCode    HTTP 状态码
     * @param headers       响应头，可为 {@code null}（视为无头）；{@code null} 键会被丢弃
     * @param body          响应体，可为 {@code null}（视为空体）
     * @param elapsedMillis 整个调用的总耗时（毫秒）
     * @param attempts      本次调用共尝试几次
     * @return 响应快照
     * @throws IllegalArgumentException {@code attempts} 小于 1，或 {@code elapsedMillis} 为负数
     */
    public static HttpResponse of(int statusCode, Map<String, List<String>> headers,
                                  byte[] body, long elapsedMillis, int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts 必须 >= 1，实际为 " + attempts);
        }
        if (elapsedMillis < 0L) {
            throw new IllegalArgumentException("elapsedMillis 不能为负数: " + elapsedMillis);
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                List<String> values = entry.getValue() == null
                        ? Collections.<String>emptyList()
                        : Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
                copied.put(entry.getKey(), values);
            }
        }
        return new HttpResponse(statusCode, Collections.unmodifiableMap(copied),
                body == null ? new byte[0] : body.clone(), elapsedMillis, attempts);
    }

    /**
     * HTTP 状态码。
     *
     * @return 状态码
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * 响应头不可变视图，键为响应原样的大小写。
     *
     * @return 头名到不可变取值列表的不可变映射
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * 大小写不敏感地取首个同名头的取值。
     *
     * @param name 头名
     * @return 首个取值；不存在时返回 {@code null}
     */
    public String header(String name) {
        List<String> values = headers(name);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 大小写不敏感地取同名头的全部取值（{@code Set-Cookie} 等场景）。
     *
     * @param name 头名
     * @return 取值列表；不存在时返回空列表（非 {@code null}）
     */
    public List<String> headers(String name) {
        if (name == null) {
            return Collections.emptyList();
        }
        List<String> merged = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                merged.addAll(entry.getValue());
            }
        }
        return Collections.unmodifiableList(merged);
    }

    /**
     * 响应体字节（防御性拷贝）。
     *
     * @return 响应体；无体时返回零长度数组
     */
    public byte[] bodyAsBytes() {
        return body.clone();
    }

    /**
     * 响应体文本：按响应头 {@code Content-Type} 的 charset 解码，缺省 UTF-8。
     *
     * @return 解码结果
     * @throws HttpException charset 名不可识别（不静默返回半截字符串）
     */
    public String bodyAsString() {
        return HttpCodec.decode(body, header(CONTENT_TYPE));
    }

    /**
     * 整个调用的总墙钟耗时（含全部尝试与重试间隔）。
     *
     * @return 毫秒数
     */
    public long elapsedMillis() {
        return elapsedMillis;
    }

    /**
     * 本次调用共尝试几次。
     *
     * @return 尝试次数，最小为 1
     */
    public int attempts() {
        return attempts;
    }
}
