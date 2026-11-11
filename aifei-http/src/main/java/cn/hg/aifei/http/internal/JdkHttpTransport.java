/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;
import cn.hg.aifei.http.HttpRequest;
import cn.hg.aifei.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 纯 JDK 传输实现：建连、应用超时、写头与体、读状态码、读响应体（含上限判定）、收集响应头、gzip 解压。
 *
 * <p>这是全模块唯一出现 {@link HttpURLConnection} 与 {@link URL} 的地方，传输细节不外溢。两条固定行为：</p>
 * <ul>
 *   <li><b>不跟随重定向</b>：3xx 原样返回，由业务判断；</li>
 *   <li><b>keep-alive</b>：读完响应体只关闭流、归还 JDK 连接缓存；仅异常与超限路径才断开连接，
 *       否则 JVM 既有的连接池属性会全部失效。</li>
 * </ul>
 *
 * <p>非 2xx 不按状态码抛异常：4xx / 5xx 的错误流与响应体照常读出并返回。</p>
 */
public final class JdkHttpTransport implements HttpTransport {

    /**
     * 默认 User-Agent 头名。
     */
    private static final String HEADER_USER_AGENT = "User-Agent";

    /**
     * 压缩协商头名。
     */
    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";

    /**
     * 响应压缩标记头名。
     */
    private static final String HEADER_CONTENT_ENCODING = "Content-Encoding";

    /**
     * gzip 标记。
     */
    private static final String GZIP = "gzip";

    /**
     * 读流缓冲区大小。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 错误响应下界：自该状态码起从错误流读响应体。
     */
    private static final int ERROR_STATUS_MIN = 400;

    private final HttpConfig config;

    /**
     * @param config 配置域，提供默认头、响应体上限与 gzip 决策
     * @throws IllegalArgumentException {@code config} 为 {@code null}
     */
    public JdkHttpTransport(HttpConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config 不能为 null");
        }
        this.config = config;
    }

    /**
     * 发出一次尝试。
     *
     * @param request 请求
     * @param attempt 本次尝试序号，从 1 起
     * @return 单次尝试的响应快照
     * @throws HttpException 网络层失败（{@link HttpIoException}）、URL 非法、响应体超限、解压失败
     */
    @Override
    public HttpResponse execute(HttpRequest request, int attempt) {
        URL url = target(request.url());
        HttpTimeouts timeouts = HttpTimeouts.resolve(request.connectTimeout(), request.readTimeout(), config);
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        boolean completed = false;
        try {
            connection = open(url, request, timeouts);
            writeBody(connection, request);
            int statusCode = connection.getResponseCode();
            byte[] body = readBody(connection, statusCode);
            body = decompress(connection, body);
            Map<String, List<String>> headers = collectHeaders(connection);
            long elapsed = System.currentTimeMillis() - start;
            completed = true;
            return HttpResponse.of(statusCode, headers, body, elapsed, attempt);
        } catch (IOException e) {
            throw new HttpIoException("请求失败: " + request.method() + " " + request.url(), e, attempt);
        } finally {
            if (connection != null && !completed) {
                connection.disconnect();
            }
        }
    }

    /**
     * 解析请求 URL。
     *
     * @param rawUrl 原始 URL
     * @return 解析结果
     * @throws HttpException URL 非法
     */
    private static URL target(String rawUrl) {
        try {
            return new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new HttpException("URL 非法: " + rawUrl, e);
        }
    }

    /**
     * 建连并装配方法、超时、请求头与默认头。
     *
     * @param url      目标地址
     * @param request  请求
     * @param timeouts 生效的超时
     * @return 已配置但尚未发送的连接
     * @throws IOException 打开连接失败
     */
    private HttpURLConnection open(URL url, HttpRequest request, HttpTimeouts timeouts) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setConnectTimeout(timeouts.connectMillis());
        connection.setReadTimeout(timeouts.readMillis());
        connection.setRequestMethod(request.method());

        Map<String, List<String>> headers = request.headers();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            List<String> values = entry.getValue();
            for (int i = 0; i < values.size(); i++) {
                if (i == 0) {
                    connection.setRequestProperty(entry.getKey(), values.get(i));
                } else {
                    connection.addRequestProperty(entry.getKey(), values.get(i));
                }
            }
        }
        if (!containsHeader(headers, HEADER_USER_AGENT) && !config.userAgent().isEmpty()) {
            connection.setRequestProperty(HEADER_USER_AGENT, config.userAgent());
        }
        if (HttpGzip.acceptEncoding(config) && !containsHeader(headers, HEADER_ACCEPT_ENCODING)) {
            connection.setRequestProperty(HEADER_ACCEPT_ENCODING, GZIP);
        }
        return connection;
    }

    /**
     * 写入请求体；无体时不做输出。
     *
     * @param connection 连接
     * @param request    请求
     * @throws IOException 写输出流失败
     */
    private static void writeBody(HttpURLConnection connection, HttpRequest request) throws IOException {
        byte[] body = request.body();
        if (body == null || body.length == 0) {
            return;
        }
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
            out.flush();
        } finally {
            out.close();
        }
    }

    /**
     * 读响应体并逐段判定上限；错误状态码从错误流读，读完即关闭流以归还连接。
     *
     * @param connection 连接
     * @param statusCode 状态码
     * @return 响应体字节
     * @throws IOException  读流失败
     * @throws HttpException 响应体超过上限
     */
    private byte[] readBody(HttpURLConnection connection, int statusCode) throws IOException {
        InputStream in = statusCode >= ERROR_STATUS_MIN ? connection.getErrorStream() : connection.getInputStream();
        if (in == null) {
            return new byte[0];
        }
        int limit = config.maxResponseBytes();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                long remaining = (long) limit + 1L - out.size();
                if (remaining <= 0L) {
                    requireWithinLimit(out.size());
                    break;
                }
                int read = in.read(buffer, 0, (int) Math.min((long) buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                out.write(buffer, 0, read);
                requireWithinLimit(out.size());
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    /**
     * 按需解压响应体；解压后重新判定上限。
     *
     * @param connection 连接（读取 {@code Content-Encoding}）
     * @param body       原始响应体
     * @return 解压后的响应体（未开启 gzip 或非 gzip 响应时原样返回）
     * @throws HttpException 解压失败或解压后超过上限
     */
    private byte[] decompress(HttpURLConnection connection, byte[] body) {
        if (body.length == 0 || !HttpGzip.acceptEncoding(config)) {
            return body;
        }
        String contentEncoding = connection.getHeaderField(HEADER_CONTENT_ENCODING);
        if (contentEncoding == null || contentEncoding.toLowerCase(Locale.ROOT).indexOf(GZIP) < 0) {
            return body;
        }
        byte[] decoded = HttpGzip.decode(body);
        requireWithinLimit(decoded.length);
        return decoded;
    }

    /**
     * 收集响应头，丢弃 {@code getHeaderFields()} 中的 {@code null} 键（状态行）。
     *
     * @param connection 连接
     * @return 原始大小写的响应头
     */
    private static Map<String, List<String>> collectHeaders(HttpURLConnection connection) {
        Map<String, List<String>> collected = new LinkedHashMap<>();
        Map<String, List<String>> raw = connection.getHeaderFields();
        if (raw == null) {
            return collected;
        }
        for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            List<String> values = entry.getValue() == null
                    ? new ArrayList<String>()
                    : new ArrayList<>(entry.getValue());
            collected.put(entry.getKey(), values);
        }
        return collected;
    }

    /**
     * 响应体上限判定：读流与解压后共用本方法。
     *
     * @param read 已读字节数
     * @throws HttpException 超过上限（消息含实际生效上限值）
     */
    private void requireWithinLimit(int read) {
        int limit = config.maxResponseBytes();
        if (read > limit) {
            throw new HttpException("响应体超过上限 " + limit + " 字节（已读 " + read + "）");
        }
    }

    /**
     * 大小写不敏感地判断请求头是否已显式设置。
     *
     * @param headers 请求头
     * @param name    头名
     * @return 已设置时 {@code true}
     */
    private static boolean containsHeader(Map<String, List<String>> headers, String name) {
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
