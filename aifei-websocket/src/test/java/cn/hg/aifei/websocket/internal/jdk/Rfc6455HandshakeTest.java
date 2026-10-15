/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal.jdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * RFC6455 握手的单元测试（设计 §5.2）：{@code Sec-WebSocket-Accept} 计算、请求头生成与响应 / 请求校验。
 *
 * <p>用例以 RFC6455 §1.3 给出的示例值为准：密钥 {@code dGhlIHNhbXBsZSBub25jZQ==} 对应的 Accept 为
 * {@code s3pPLMBiTxaQ9kYGzzhZRbK+xOo=}。</p>
 */
public class Rfc6455HandshakeTest {

    /**
     * RFC6455 示例密钥。
     */
    private static final String RFC_KEY = "dGhlIHNhbXBsZSBub25jZQ==";

    /**
     * RFC6455 示例密钥对应的 {@code Sec-WebSocket-Accept}。
     */
    private static final String RFC_ACCEPT = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=";

    /**
     * Accept 计算必须与 RFC6455 示例一致。
     *
     * @throws IOException 计算失败
     */
    @Test
    public void shouldComputeRfcExampleAccept() throws IOException {
        assertEquals(RFC_ACCEPT, Rfc6455Handshake.acceptFor(RFC_KEY));
    }

    /**
     * 生成的 {@code Sec-WebSocket-Key} 必须是 16 字节随机数的 Base64 文本，且两次生成不相同。
     */
    @Test
    public void shouldGenerateRandomSixteenByteKey() {
        String first = Rfc6455Handshake.newKey();
        String second = Rfc6455Handshake.newKey();
        assertNotNull(first);
        assertEquals(16, Base64.getDecoder().decode(first).length);
        assertTrue("两次生成的密钥不应相同", !first.equals(second));
    }

    /**
     * 客户端请求头必须含 RFC6455 要求的Upgrade 头、版本 13 与附加请求头。
     *
     * @throws IOException 写出失败
     */
    @Test
    public void shouldWriteClientRequestWithRequiredHeaders() throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer t0ken");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rfc6455Handshake.writeRequest(out, "example.test:8080", "/chat?room=1", RFC_KEY, headers);

        String request = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        assertTrue(request.startsWith("GET /chat?room=1 HTTP/1.1\r\n"));
        assertTrue(request.contains("Host: example.test:8080\r\n"));
        assertTrue(request.contains("Upgrade: websocket\r\n"));
        assertTrue(request.contains("Connection: Upgrade\r\n"));
        assertTrue(request.contains("Sec-WebSocket-Key: " + RFC_KEY + "\r\n"));
        assertTrue(request.contains("Sec-WebSocket-Version: 13\r\n"));
        assertTrue(request.contains("Authorization: Bearer t0ken\r\n"));
        assertTrue(request.endsWith("\r\n\r\n"));
    }

    /**
     * 服务端解析请求：方法、目标与头名大小写不敏感。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldParseClientRequest() throws IOException {
        InputStream in = stream("GET /chat HTTP/1.1\r\n"
                + "Host: 127.0.0.1\r\n"
                + "upgrade: WebSocket\r\n"
                + "Connection: keep-alive, Upgrade\r\n"
                + "sec-websocket-key: " + RFC_KEY + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n");

        Rfc6455Handshake.Request request = Rfc6455Handshake.readRequest(in);
        assertEquals("GET", request.method());
        assertEquals("/chat", request.target());
        assertEquals(RFC_KEY, request.get("SEC-WEBSOCKET-KEY"));

        assertEquals(RFC_KEY, Rfc6455Handshake.requireKey(request));
    }

    /**
     * 非 {@code GET} 方法的请求被拒。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldRejectNonGetRequest() throws IOException {
        Rfc6455Handshake.Request request = Rfc6455Handshake.readRequest(stream("POST /chat HTTP/1.1\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + RFC_KEY + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n"));
        try {
            Rfc6455Handshake.requireKey(request);
            fail("非 GET 请求应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("GET"));
        }
    }

    /**
     * 协议版本不是 13 的请求被拒。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldRejectUnsupportedVersion() throws IOException {
        Rfc6455Handshake.Request request = Rfc6455Handshake.readRequest(stream("GET /chat HTTP/1.1\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + RFC_KEY + "\r\n"
                + "Sec-WebSocket-Version: 8\r\n"
                + "\r\n"));
        try {
            Rfc6455Handshake.requireKey(request);
            fail("版本不是 13 的请求应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("version"));
        }
    }

    /**
     * 缺少 {@code Sec-WebSocket-Key} 的请求被拒。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldRejectMissingKey() throws IOException {
        Rfc6455Handshake.Request request = Rfc6455Handshake.readRequest(stream("GET /chat HTTP/1.1\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "\r\n"));
        try {
            Rfc6455Handshake.requireKey(request);
            fail("缺少 Sec-WebSocket-Key 的请求应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Sec-WebSocket-Key"));
        }
    }

    /**
     * 服务端握手成功响应必须是 101 且带回算出的 Accept。
     *
     * @throws IOException 写出失败
     */
    @Test
    public void shouldWriteServerAcceptResponse() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rfc6455Handshake.writeResponse(out, RFC_ACCEPT);

        String response = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        assertTrue(response.startsWith("HTTP/1.1 101 Switching Protocols\r\n"));
        assertTrue(response.contains("Sec-WebSocket-Accept: " + RFC_ACCEPT + "\r\n"));
        assertTrue(response.endsWith("\r\n\r\n"));
    }

    /**
     * 客户端接受 101 且 Accept 一致的响应。
     *
     * @throws IOException 解析或校验失败
     */
    @Test
    public void shouldAcceptValidHandshakeResponse() throws IOException {
        Rfc6455Handshake.Response response = Rfc6455Handshake.readResponse(stream("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + RFC_ACCEPT + "\r\n"
                + "\r\n"));
        assertEquals(101, response.statusCode());
        assertEquals(RFC_ACCEPT, response.get("sec-websocket-accept"));
        Rfc6455Handshake.verifyResponse(response, RFC_ACCEPT);
    }

    /**
     * 状态不是 101 的响应被拒。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldRejectNonSwitchingProtocolStatus() throws IOException {
        Rfc6455Handshake.Response response = Rfc6455Handshake.readResponse(stream("HTTP/1.1 200 OK\r\n"
                + "Sec-WebSocket-Accept: " + RFC_ACCEPT + "\r\n"
                + "\r\n"));
        try {
            Rfc6455Handshake.verifyResponse(response, RFC_ACCEPT);
            fail("状态不是 101 的响应应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("101"));
        }
    }

    /**
     * Accept 与本地计算值不一致的响应被拒。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldRejectMismatchedAccept() throws IOException {
        Rfc6455Handshake.Response response = Rfc6455Handshake.readResponse(stream("HTTP/1.1 101 Switching Protocols\r\n"
                + "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"
                + "\r\n"));
        try {
            Rfc6455Handshake.verifyResponse(response, "not-the-expected-accept");
            fail("Accept 不一致的响应应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Accept"));
        }
    }

    /**
     * 缺少 Accept 头的响应被拒。
     *
     * @throws IOException 解析失败
     */
    @Test
    public void shouldRejectMissingAccept() throws IOException {
        Rfc6455Handshake.Response response = Rfc6455Handshake.readResponse(stream("HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "\r\n"));
        try {
            Rfc6455Handshake.verifyResponse(response, RFC_ACCEPT);
            fail("缺少 Accept 的响应应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Sec-WebSocket-Accept"));
        }
    }

    /**
     * 握手失败响应为 400 且声明关闭连接。
     *
     * @throws IOException 写出失败
     */
    @Test
    public void shouldWriteBadRequestResponse() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rfc6455Handshake.writeBadRequest(out);

        String response = new String(out.toByteArray(), StandardCharsets.US_ASCII);
        assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"));
        assertTrue(response.contains("Connection: close\r\n"));
    }

    /**
     * 空响应（连接被对端直接关闭）按握手失败处理。
     */
    @Test
    public void shouldRejectEmptyResponse() {
        try {
            Rfc6455Handshake.readResponse(stream(""));
            fail("空响应应被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
    }

    /**
     * 由文本构造输入流。
     *
     * @param text 握手报文
     * @return 输入流
     */
    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
    }
}
