/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP Upgrade 握手的唯一定义点（RFC6455 §1.3 / §4.1 / §4.2）：客户端请求头生成与响应校验、服务端请求解析与
 * {@code Sec-WebSocket-Accept} 计算，都只在本类出现一次。
 *
 * <p>{@code Sec-WebSocket-Accept} 的计算式为
 * {@code Base64(SHA1(Sec-WebSocket-Key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"))}，GUID 字面量与 SHA-1 /
 * Base64 计算只在本类出现。本类属内部实现，不是公共 API。</p>
 *
 * <p>线程安全：本类无状态（随机数源是线程安全的 {@link SecureRandom}），方法可被多线程并发调用。</p>
 */
public final class Rfc6455Handshake {

    /**
     * RFC6455 规定的握手 GUID，参与 {@code Sec-WebSocket-Accept} 计算。
     */
    public static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /**
     * 请求头名：{@code Sec-WebSocket-Key}。
     */
    public static final String HEADER_KEY = "Sec-WebSocket-Key";

    /**
     * 请求头名：{@code Sec-WebSocket-Accept}。
     */
    public static final String HEADER_ACCEPT = "Sec-WebSocket-Accept";

    /**
     * RFC6455 要求的协议版本号。
     */
    public static final String VERSION = "13";

    /**
     * 握手头块的大小上限（字节）；超出即按握手失败处理，防止恶意对端耗尽内存。
     */
    private static final int MAX_HEADER_BYTES = 16384;

    /**
     * {@code Sec-WebSocket-Key} 的随机字节数（RFC6455 要求 16 字节）。
     */
    private static final int KEY_BYTES = 16;

    /**
     * 握手随机数源；用于生成 {@code Sec-WebSocket-Key}。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 禁止创建工具类实例。
     */
    private Rfc6455Handshake() {
    }

    /**
     * 生成一个 {@code Sec-WebSocket-Key}：16 字节随机数的 Base64 文本。
     *
     * @return 新建的握手密钥；不为 {@code null}
     */
    public static String newKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 按 RFC6455 计算 {@code Sec-WebSocket-Accept}。
     *
     * @param key 对端发来的 {@code Sec-WebSocket-Key}
     * @return 对应的 {@code Sec-WebSocket-Accept} 取值
     * @throws IllegalArgumentException {@code key} 为 {@code null}
     * @throws IOException SHA-1 算法在当前 JDK 上不可用（运行态故障，按握手失败收敛）
     */
    public static String acceptFor(String key) throws IOException {
        if (key == null) {
            throw new IllegalArgumentException("key can not be null");
        }
        byte[] digest;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            digest = sha1.digest((key + GUID).getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 is not available", e);
        }
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * 写出客户端的 HTTP Upgrade 请求；附加请求头在固定头之后、空行之前写出。
     *
     * @param out 目标输出流
     * @param host {@code Host} 请求头取值
     * @param target 请求目标（路径与查询串，至少为 {@code "/"}）
     * @param key 本次握手的 {@code Sec-WebSocket-Key}
     * @param headers 附加请求头；可为 {@code null} 或空
     * @throws IOException 写出失败
     */
    public static void writeRequest(OutputStream out, String host, String target, String key,
            Map<String, String> headers) throws IOException {
        StringBuilder builder = new StringBuilder(256);
        builder.append("GET ").append(target).append(" HTTP/1.1\r\n");
        builder.append("Host: ").append(host).append("\r\n");
        builder.append("Upgrade: websocket\r\n");
        builder.append("Connection: Upgrade\r\n");
        builder.append(HEADER_KEY).append(": ").append(key).append("\r\n");
        builder.append("Sec-WebSocket-Version: ").append(VERSION).append("\r\n");
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                builder.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }
        }
        builder.append("\r\n");
        out.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 读取并解析客户端的握手请求；请求行与请求头一并返回。
     *
     * @param in 源输入流
     * @return 解析后的请求
     * @throws IOException 读取失败、连接提前结束或头块超出上限
     */
    public static Request readRequest(InputStream in) throws IOException {
        String startLine = readLine(in);
        if (startLine == null || startLine.isEmpty()) {
            throw new IOException("empty handshake request");
        }
        return new Request(startLine, readHeaders(in));
    }

    /**
     * 校验客户端握手请求并返回其 {@code Sec-WebSocket-Key}。
     *
     * <p>校验项：方法为 {@code GET}、{@code Upgrade} 头含 {@code websocket}、{@code Connection} 头含
     * {@code Upgrade}、{@code Sec-WebSocket-Version} 为 {@code 13}，且 {@code Sec-WebSocket-Key} 存在。</p>
     *
     * @param request 已解析的请求
     * @return 请求携带的 {@code Sec-WebSocket-Key}
     * @throws IOException 任一校验项不通过
     */
    public static String requireKey(Request request) throws IOException {
        if (!"GET".equals(request.method())) {
            throw new IOException("handshake request method must be GET but was " + request.method());
        }
        if (!containsToken(request.get("Upgrade"), "websocket")) {
            throw new IOException("handshake request must upgrade to websocket");
        }
        if (!containsToken(request.get("Connection"), "Upgrade")) {
            throw new IOException("handshake request must declare Connection: Upgrade");
        }
        String version = request.get("Sec-WebSocket-Version");
        if (version == null) {
            throw new IOException("handshake request must carry Sec-WebSocket-Version");
        }
        if (!VERSION.equals(version.trim())) {
            throw new IOException("unsupported websocket version: " + version);
        }
        String key = request.get(HEADER_KEY);
        if (key == null || key.trim().isEmpty()) {
            throw new IOException("handshake request must carry Sec-WebSocket-Key");
        }
        return key.trim();
    }

    /**
     * 写出服务端握手成功响应（{@code 101 Switching Protocols}）。
     *
     * @param out 目标输出流
     * @param accept {@code Sec-WebSocket-Accept} 取值
     * @throws IOException 写出失败
     */
    public static void writeResponse(OutputStream out, String accept) throws IOException {
        StringBuilder builder = new StringBuilder(256);
        builder.append("HTTP/1.1 101 Switching Protocols\r\n");
        builder.append("Upgrade: websocket\r\n");
        builder.append("Connection: Upgrade\r\n");
        builder.append(HEADER_ACCEPT).append(": ").append(accept).append("\r\n");
        builder.append("\r\n");
        out.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 写出服务端握手失败响应（{@code 400 Bad Request}）；调用方随后应关闭该连接。
     *
     * @param out 目标输出流
     * @throws IOException 写出失败
     */
    public static void writeBadRequest(OutputStream out) throws IOException {
        StringBuilder builder = new StringBuilder(128);
        builder.append("HTTP/1.1 400 Bad Request\r\n");
        builder.append("Connection: close\r\n");
        builder.append("Content-Length: 0\r\n");
        builder.append("\r\n");
        out.write(builder.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 读取并解析服务端握手响应。
     *
     * @param in 源输入流
     * @return 解析后的响应
     * @throws IOException 读取失败、连接提前结束、头块超出上限或状态行非法
     */
    public static Response readResponse(InputStream in) throws IOException {
        String startLine = readLine(in);
        if (startLine == null || startLine.isEmpty()) {
            throw new IOException("empty handshake response");
        }
        String[] parts = startLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("malformed handshake response status line: " + startLine);
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IOException("malformed handshake response status line: " + startLine);
        }
        return new Response(statusCode, readHeaders(in));
    }

    /**
     * 校验服务端握手响应：状态必须为 {@code 101}，且 {@code Sec-WebSocket-Accept} 与本地计算值一致。
     *
     * @param response 已解析的响应
     * @param expectedAccept 本地按 {@link #acceptFor(String)} 计算出的期望值
     * @throws IOException 状态不是 {@code 101} 或 {@code Sec-WebSocket-Accept} 不一致
     */
    public static void verifyResponse(Response response, String expectedAccept) throws IOException {
        if (response.statusCode() != 101) {
            throw new IOException("handshake response status must be 101 but was " + response.statusCode());
        }
        String actual = response.get(HEADER_ACCEPT);
        if (actual == null) {
            throw new IOException("handshake response must carry Sec-WebSocket-Accept");
        }
        if (!actual.trim().equals(expectedAccept)) {
            throw new IOException("handshake response Sec-WebSocket-Accept mismatch");
        }
    }

    // ─────────── 内部实现 ───────────

    /**
     * 读取一行（不含行尾 {@code CRLF}）；流已结束且未读到任何字节时返回 {@code null}。
     *
     * @param in 源输入流
     * @return 该行文本；流首即结束时为 {@code null}
     * @throws IOException 读取失败或单行超出头块上限
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(128);
        int current;
        while ((current = in.read()) != -1) {
            if (current == '\n') {
                return new String(line.toByteArray(), StandardCharsets.UTF_8);
            }
            if (current != '\r') {
                line.write(current);
            }
            if (line.size() > MAX_HEADER_BYTES) {
                throw new IOException("handshake header line is too large");
            }
        }
        if (line.size() == 0) {
            return null;
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 读取头块（至首个空行）并按小写名建索引，便于大小写不敏感地取头。
     *
     * @param in 源输入流
     * @return 小写头名到取值的映射；不为 {@code null}
     * @throws IOException 读取失败或头块超出上限
     */
    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        int total = 0;
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            total += line.length();
            if (total > MAX_HEADER_BYTES) {
                throw new IOException("handshake header block is too large");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }
        return headers;
    }

    /**
     * 判断以逗号分隔的头取值中是否含指定记号（忽略大小写与空白）。
     *
     * @param value 头取值；可为 {@code null}
     * @param token 期望的记号
     * @return 含该记号时 {@code true}
     */
    private static boolean containsToken(String value, String token) {
        if (value == null) {
            return false;
        }
        String[] tokens = value.split(",");
        for (String item : tokens) {
            if (token.equalsIgnoreCase(item.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 握手请求：请求行与请求头。
     */
    public static final class Request {

        private final String startLine;

        private final Map<String, String> headers;

        private Request(String startLine, Map<String, String> headers) {
            this.startLine = startLine;
            this.headers = headers;
        }

        /**
         * 返回请求方法（请求行首段）。
         *
         * @return 请求方法；请求行非法时为 {@code null}
         */
        public String method() {
            String[] parts = startLine.split(" ");
            return parts.length > 0 ? parts[0] : null;
        }

        /**
         * 返回请求目标（请求行次段）。
         *
         * @return 请求目标；请求行无次段时为 {@code null}
         */
        public String target() {
            String[] parts = startLine.split(" ");
            return parts.length > 1 ? parts[1] : null;
        }

        /**
         * 按大小写不敏感的头名取请求头。
         *
         * @param name 头名
         * @return 头取值；不存在时为 {@code null}
         */
        public String get(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /**
         * 返回全部请求头（键为小写头名）的只读视图。
         *
         * @return 请求头映射；不为 {@code null}
         */
        public Map<String, String> headers() {
            return Collections.unmodifiableMap(headers);
        }
    }

    /**
     * 握手响应：状态码与响应头。
     */
    public static final class Response {

        private final int statusCode;

        private final Map<String, String> headers;

        private Response(int statusCode, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.headers = headers;
        }

        /**
         * 返回 HTTP 状态码。
         *
         * @return 状态码
         */
        public int statusCode() {
            return statusCode;
        }

        /**
         * 按大小写不敏感的头名取响应头。
         *
         * @param name 头名
         * @return 头取值；不存在时为 {@code null}
         */
        public String get(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /**
         * 返回全部响应头（键为小写头名）的只读视图。
         *
         * @return 响应头映射；不为 {@code null}
         */
        public Map<String, String> headers() {
            return Collections.unmodifiableMap(headers);
        }
    }
}
