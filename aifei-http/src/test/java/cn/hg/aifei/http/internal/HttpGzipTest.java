/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.http.HttpException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

/**
 * gzip（AC-R-24）：默认关闭、开启后发送协商头、解压失败即失败。
 */
public class HttpGzipTest {

    /**
     * 默认不发送 {@code Accept-Encoding}。
     */
    @Test
    public void shouldBeDisabledByDefault() {
        assertFalse(HttpGzip.acceptEncoding(HttpConfig.parse(new Properties())));
        assertFalse(HttpGzip.acceptEncoding(null));
    }

    /**
     * {@code http.gzip=true} 时才接受 gzip。
     */
    @Test
    public void shouldAcceptEncodingWhenEnabled() {
        Properties properties = new Properties();
        properties.setProperty(HttpConfig.KEY_GZIP, "true");

        assertTrue(HttpGzip.acceptEncoding(HttpConfig.parse(properties)));
    }

    /**
     * 解压结果与原文一致。
     *
     * @throws IOException 压缩失败
     */
    @Test
    public void shouldDecompressPayload() throws IOException {
        byte[] raw = "中文 gzip 载荷".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(raw, HttpGzip.decode(gzip(raw)));
    }

    /**
     * 空输入原样返回。
     */
    @Test
    public void shouldPassThroughEmptyPayload() {
        assertArrayEquals(new byte[0], HttpGzip.decode(new byte[0]));
    }

    /**
     * 非 gzip 数据 → {@link HttpException}（消息固定为「gzip 解压失败」）。
     */
    @Test
    public void shouldFailOnBrokenPayload() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpGzip.decode("not-gzip".getBytes(StandardCharsets.UTF_8)));

        assertTrue(e.getMessage().contains("gzip 解压失败"));
        assertTrue("cause 应为底层 IO 异常", e.getCause() != null);
    }

    /**
     * 压缩字节。
     *
     * @param raw 原文
     * @return 压缩结果
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
}
