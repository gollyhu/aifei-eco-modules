/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.pay;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.pay.core.PayHttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

/**
 * 基于 JDK {@link HttpURLConnection} 的默认实现（零第三方依赖）。
 *
 * <p>可注入 {@link SSLContext}：用于企业网关自签证书信任，或商户证书 mTLS 场景。
 */
public class JdkPayHttpKit implements PayHttpKit {

    private final int connectTimeout;
    private final int readTimeout;
    private final SSLContext sslContext;

    public JdkPayHttpKit() {
        this(5000, 10000, null);
    }

    public JdkPayHttpKit(SSLContext sslContext) {
        this(5000, 10000, sslContext);
    }

    public JdkPayHttpKit(int connectTimeout, int readTimeout, SSLContext sslContext) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.sslContext = sslContext;
    }

    @Override
    public PayHttpResponse get(String url, Map<String, String> headers) {
        return request("GET", url, headers, null);
    }

    @Override
    public PayHttpResponse post(String url, Map<String, String> headers, String jsonBody) {
        byte[] body = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        return request("POST", url, headers, body);
    }

    private PayHttpResponse request(String method, String url, Map<String, String> headers, byte[] body) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            connection.setRequestMethod(method);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body);
                }
            }

            int statusCode = connection.getResponseCode();
            String responseBody = new String(
                    readBytes(statusCode >= 200 && statusCode < 300
                            ? connection.getInputStream() : connection.getErrorStream()),
                    StandardCharsets.UTF_8);
            return new PayHttpResponse(statusCode, flattenHeaders(connection), responseBody);
        } catch (IOException e) {
            throw new WxApiException("微信支付接口请求失败: " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        if (sslContext != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslContext.getSocketFactory());
        }
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

    private static Map<String, String> flattenHeaders(HttpURLConnection connection) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                headers.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return headers;
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        if (is == null) {
            return new byte[0];
        }
        try (InputStream input = is) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }
}
