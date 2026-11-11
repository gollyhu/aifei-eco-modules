/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * 测试用回环端点：JDK 自带 {@link HttpServer} 绑定 {@code 127.0.0.1} 随机端口，全程不出本机。
 *
 * <p>可控行为：回显 query / 请求头 / 请求体、指定状态码、指定延迟、前 N 次 5xx、gzip 压缩响应、302 重定向、
 * 大响应体、GBK 响应。请求计数按端点累加，供重试与「未发送」类断言使用。</p>
 */
public final class LoopbackServer implements AutoCloseable {

    /**
     * 绑定的回环地址。
     */
    private static final String HOST = "127.0.0.1";

    /**
     * 回显用的自定义头名。
     */
    private static final String DEMO_HEADER = "X-Demo";

    /**
     * gzip 端点返回的原文。
     */
    private static final String GZIP_PAYLOAD = "gzip-payload";

    /**
     * GBK 端点返回的原文。
     */
    private static final String GBK_PAYLOAD = "中文响应";

    private final HttpServer server;

    private final ExecutorService executor;

    private final AtomicInteger requestCount = new AtomicInteger();

    private final AtomicInteger flakyCount = new AtomicInteger();

    /**
     * 启动端点服务。
     *
     * @throws IOException 绑定端口失败
     */
    public LoopbackServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
        this.executor = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "loopback-server");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.server.setExecutor(executor);
        this.server.createContext("/", new Dispatcher());
        this.server.start();
    }

    /**
     * 服务根地址。
     *
     * @return 形如 {@code http://127.0.0.1:端口}
     */
    public String baseUrl() {
        return "http://" + HOST + ":" + server.getAddress().getPort();
    }

    /**
     * 拼接完整端点地址。
     *
     * @param pathAndQuery 以 {@code /} 开头的路径，可含 query
     * @return 完整地址
     */
    public String url(String pathAndQuery) {
        return baseUrl() + pathAndQuery;
    }

    /**
     * 已收到的请求总数。
     *
     * @return 请求数
     */
    public int requestCount() {
        return requestCount.get();
    }

    /**
     * 清空计数。
     */
    public void reset() {
        requestCount.set(0);
        flakyCount.set(0);
    }

    /**
     * 停止服务并释放线程。
     */
    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    /**
     * 按路径分派响应；所有响应在结束后统一关闭交换。
     */
    private final class Dispatcher implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            try {
                dispatch(exchange);
            } finally {
                exchange.close();
            }
        }

        /**
         * 分派单个请求。
         *
         * @param exchange 交换对象
         * @throws IOException 写响应失败
         */
        private void dispatch(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String rawQuery = exchange.getRequestURI().getRawQuery();
            if ("/echo/query".equals(path)) {
                exchange.getResponseHeaders().add("Set-Cookie", "a=1");
                exchange.getResponseHeaders().add("Set-Cookie", "b=2");
                send(exchange, 200, "text/plain; charset=UTF-8", text(rawQuery == null ? "" : rawQuery));
            } else if ("/echo/header".equals(path)) {
                String value = exchange.getRequestHeaders().getFirst(DEMO_HEADER);
                send(exchange, 200, "text/plain; charset=UTF-8", text(value == null ? "" : value));
            } else if ("/echo/accept-encoding".equals(path)) {
                String value = exchange.getRequestHeaders().getFirst("Accept-Encoding");
                send(exchange, 200, "text/plain; charset=UTF-8", text("accept-encoding=" + value));
            } else if ("/echo/body".equals(path)) {
                byte[] received = readAll(exchange.getRequestBody());
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                send(exchange, 200, contentType == null ? "application/octet-stream" : contentType, received);
            } else if ("/status".equals(path)) {
                int code = param(rawQuery, "code", 200);
                send(exchange, code, "text/plain; charset=UTF-8", text("status=" + code));
            } else if ("/delay".equals(path)) {
                sleep(param(rawQuery, "ms", 100));
                send(exchange, 200, "text/plain; charset=UTF-8", text("delayed"));
            } else if ("/flaky".equals(path)) {
                int fail = param(rawQuery, "fail", 1);
                int current = flakyCount.incrementAndGet();
                boolean failNow = current <= fail;
                send(exchange, failNow ? 503 : 200, "text/plain; charset=UTF-8", text("attempt=" + current));
            } else if ("/gzip".equals(path)) {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                send(exchange, 200, "text/plain; charset=UTF-8", gzip(text(GZIP_PAYLOAD)));
            } else if ("/redirect".equals(path)) {
                exchange.getResponseHeaders().set("Location", "/echo/query");
                send(exchange, 302, "text/plain; charset=UTF-8", text("redirect"));
            } else if ("/big".equals(path)) {
                byte[] body = new byte[param(rawQuery, "bytes", 16)];
                java.util.Arrays.fill(body, (byte) 'a');
                send(exchange, 200, "application/octet-stream", body);
            } else if ("/gbk".equals(path)) {
                send(exchange, 200, "text/plain; charset=GBK",
                        GBK_PAYLOAD.getBytes(Charset.forName("GBK")));
            } else {
                send(exchange, 404, "text/plain; charset=UTF-8", text("not found"));
            }
        }
    }

    /**
     * 写出响应。
     *
     * @param exchange    交换对象
     * @param status      状态码
     * @param contentType 响应媒体类型
     * @param body        响应体
     * @throws IOException 写失败
     */
    private static void send(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (body.length == 0) {
            exchange.sendResponseHeaders(status, -1L);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(body);
            out.flush();
        } finally {
            out.close();
        }
    }

    /**
     * 读取请求体全部字节。
     *
     * @param in 请求体流
     * @return 字节
     * @throws IOException 读失败
     */
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    /**
     * gzip 压缩。
     *
     * @param raw 原文
     * @return 压缩字节
     * @throws IOException 压缩失败
     */
    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
            gzip.finish();
        }
        return out.toByteArray();
    }

    /**
     * 取 query 参数值。
     *
     * @param rawQuery 原始 query
     * @param name     参数名
     * @param fallback 缺省值
     * @return 参数值
     */
    private static int param(String rawQuery, String name, int fallback) {
        if (rawQuery == null) {
            return fallback;
        }
        String[] pairs = rawQuery.split("&");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            int assign = pair.indexOf('=');
            String key = assign < 0 ? pair : pair.substring(0, assign);
            if (name.equals(key)) {
                String value = assign < 0 ? "" : pair.substring(assign + 1);
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return fallback;
                }
            }
        }
        return fallback;
    }

    /**
     * 按毫秒等待，用于制造读超时。
     *
     * @param millis 毫秒数
     */
    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 以 UTF-8 编码文本。
     *
     * @param value 文本
     * @return 字节
     */
    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
