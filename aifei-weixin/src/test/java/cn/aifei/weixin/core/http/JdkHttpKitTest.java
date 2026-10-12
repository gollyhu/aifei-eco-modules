/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.aifei.weixin.common.WxApiException;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/**
 * JDK HTTP 实现（S3 验证标准 ⑥⑦）：multipart 拼装 + 传输失败转异常。不访问外网。
 */
public class JdkHttpKitTest {

    @Test
    public void networkFailureThrowsWxApiException() {
        // 127.0.0.1:1 必然拒绝连接，模拟网络不可达（不依赖外网）
        JdkHttpKit http = new JdkHttpKit(200, 200);
        try {
            http.get("http://127.0.0.1:1/cgi-bin/token");
            fail("网络不可达必须抛 WxApiException");
        } catch (WxApiException expected) {
            assertTrue(expected.getMessage().contains("微信接口请求失败"));
        }
    }

    @Test(timeout = 5000)
    public void downloadHonorsReadTimeout() throws Exception {
        // 服务端接受连接后不回响应：download 若漏设超时会无限阻塞
        try (ServerSocket server = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    Thread.sleep(3000);
                } catch (Exception ignore) {
                    // 测试结束时连接被关闭
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            JdkHttpKit http = new JdkHttpKit(500, 300);
            long start = System.currentTimeMillis();
            try {
                http.download("http://127.0.0.1:" + server.getLocalPort() + "/cgi-bin/media/get");
                fail("服务端不响应时读超时必须抛 WxApiException");
            } catch (WxApiException expected) {
                assertTrue("download 未设置超时会无限阻塞", System.currentTimeMillis() - start < 3000);
            }
        }
    }

    @Test
    public void multipartBodyCarriesFieldAndFileNames() throws Exception {
        File file = File.createTempFile("aifei-weixin-media", ".png");
        file.deleteOnExit();
        Files.write(file.toPath(), "FILEDATA".getBytes(StandardCharsets.UTF_8));

        Map<String, String> fields = new HashMap<>();
        fields.put("type", "image");
        Map<String, File> files = Collections.singletonMap("media", file);

        String boundary = "----test-boundary";
        String body = new String(JdkHttpKit.buildMultipartBody(boundary, fields, files), StandardCharsets.UTF_8);

        assertTrue(body.contains("--" + boundary + "\r\n"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"type\"\r\n\r\nimage\r\n"));
        assertTrue(body.contains("Content-Disposition: form-data; name=\"media\"; filename=\"" + file.getName() + "\""));
        assertTrue(body.contains("FILEDATA"));
        assertTrue(body.endsWith("--" + boundary + "--\r\n"));
    }

    @Test
    public void multipartBodyWithoutBoundaryOverflowOfFields() {
        String body = new String(JdkHttpKit.buildMultipartBody("b", null, null), StandardCharsets.UTF_8);
        assertEquals("--b--\r\n", body);
    }
}
