/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.http;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.core.encrypt.RandomKit;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * 基于 JDK {@link HttpURLConnection} 的默认实现（零第三方依赖）。
 *
 * <p>传输层失败（网络不可达 / 超时 / HTTP 状态码非 200 / 读取异常）统一抛
 * {@link WxApiException}。
 */
public class JdkHttpKit implements HttpKit {

    private final int connectTimeout;
    private final int readTimeout;

    public JdkHttpKit() {
        this(5000, 10000);
    }

    public JdkHttpKit(int connectTimeout, int readTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public String get(String url) {
        return request("GET", url, null, null);
    }

    @Override
    public String post(String url, String jsonBody) {
        byte[] body = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        return request("POST", url, body, "application/json; charset=utf-8");
    }

    @Override
    public String postMultipart(String url, Map<String, String> fields, Map<String, File> files) {
        String boundary = "----aifei-weixin-" + RandomKit.randomStr(16);
        byte[] body = buildMultipartBody(boundary, fields, files);
        return request("POST", url, body, "multipart/form-data; boundary=" + boundary);
    }

    @Override
    public byte[] download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            int statusCode = connection.getResponseCode();
            boolean ok = statusCode >= 200 && statusCode < 300;
            byte[] bytes = readBytes(ok ? connection.getInputStream() : connection.getErrorStream());
            if (!ok) {
                throw new WxApiException("微信接口下载失败，HTTP 状态码 " + statusCode + " url=" + url);
            }
            return bytes;
        } catch (WxApiException e) {
            throw e;
        } catch (IOException e) {
            throw new WxApiException("微信接口下载失败: " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public byte[] postDownload(String url, String jsonBody) {
        byte[] body = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            int statusCode = connection.getResponseCode();
            boolean ok = statusCode >= 200 && statusCode < 300;
            byte[] bytes = readBytes(ok ? connection.getInputStream() : connection.getErrorStream());
            if (!ok) {
                throw new WxApiException("微信接口下载失败，HTTP 状态码 " + statusCode + " url=" + url);
            }
            return bytes;
        } catch (WxApiException e) {
            throw e;
        } catch (IOException e) {
            throw new WxApiException("微信接口下载失败: " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String request(String method, String url, byte[] body, String contentType) {
        HttpURLConnection connection = null;
        try {
            connection = open(url);
            connection.setRequestMethod(method);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body);
                }
            }

            int statusCode = connection.getResponseCode();
            boolean ok = statusCode >= 200 && statusCode < 300;
            String response = new String(readBytes(ok ? connection.getInputStream() : connection.getErrorStream()),
                    StandardCharsets.UTF_8);
            if (!ok) {
                throw new WxApiException("微信接口 HTTP 状态码异常：" + statusCode + " url=" + url + " body=" + response);
            }
            return response;
        } catch (WxApiException e) {
            throw e;
        } catch (IOException e) {
            throw new WxApiException("微信接口请求失败: " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

    /**
     * 手工拼装 multipart/form-data 请求体（微信素材上传只需字段 + 单个文件，无需通用 multipart 库）
     */
    static byte[] buildMultipartBody(String boundary, Map<String, String> fields, Map<String, File> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (fields != null) {
                for (Map.Entry<String, String> entry : fields.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }
                    writeUtf8(out, "--" + boundary + "\r\n");
                    writeUtf8(out, "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n");
                    writeUtf8(out, entry.getValue());
                    writeUtf8(out, "\r\n");
                }
            }
            if (files != null) {
                for (Map.Entry<String, File> entry : files.entrySet()) {
                    File file = entry.getValue();
                    if (file == null) {
                        continue;
                    }
                    writeUtf8(out, "--" + boundary + "\r\n");
                    writeUtf8(out, "Content-Disposition: form-data; name=\"" + entry.getKey()
                            + "\"; filename=\"" + file.getName() + "\"\r\n");
                    writeUtf8(out, "Content-Type: application/octet-stream\r\n\r\n");
                    out.write(Files.readAllBytes(file.toPath()));
                    writeUtf8(out, "\r\n");
                }
            }
            writeUtf8(out, "--" + boundary + "--\r\n");
        } catch (IOException e) {
            throw new WxApiException("构建 multipart 请求体失败", e);
        }
        return out.toByteArray();
    }

    private static void writeUtf8(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
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
