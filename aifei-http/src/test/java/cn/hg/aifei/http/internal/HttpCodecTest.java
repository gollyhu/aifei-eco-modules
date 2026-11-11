/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cn.hg.aifei.http.HttpException;
import java.nio.charset.Charset;
import org.junit.Test;

/**
 * 编解码（AC-R-21 / AC-R-15 的解码失败分支）：charset 解析、往返一致、非法 charset 失败。
 */
public class HttpCodecTest {

    /**
     * 未声明 charset 时按 UTF-8。
     */
    @Test
    public void shouldDefaultToUtf8() {
        assertEquals("UTF-8", HttpCodec.charsetOf(null));
        assertEquals("UTF-8", HttpCodec.charsetOf("text/plain"));
        assertEquals("UTF-8", HttpCodec.charsetOf("text/plain; charset="));
    }

    /**
     * charset 参数大小写不敏感、可带引号、可后跟其它参数。
     */
    @Test
    public void shouldParseCharsetParameter() {
        assertEquals("GBK", HttpCodec.charsetOf("text/plain; charset=GBK"));
        assertEquals("GBK", HttpCodec.charsetOf("text/plain;charset=gbk; x=1"));
        assertEquals("UTF-8", HttpCodec.charsetOf("application/json; Charset=\"UTF-8\""));
    }

    /**
     * AC-R-21：中文 + emoji 经 UTF-8 往返一致。
     */
    @Test
    public void shouldRoundTripUtf8Text() {
        String text = "中文🙂aifei";

        byte[] encoded = HttpCodec.encode(text, "text/plain; charset=UTF-8");

        assertArrayEquals(text.getBytes(Charset.forName("UTF-8")), encoded);
        assertEquals(text, HttpCodec.decode(encoded, "text/plain; charset=UTF-8"));
    }

    /**
     * AC-R-21：GBK 响应按 GBK 解码，与 UTF-8 结果不同。
     */
    @Test
    public void shouldDecodeGbk() {
        byte[] gbk = "中文响应".getBytes(Charset.forName("GBK"));

        assertEquals("中文响应", HttpCodec.decode(gbk, "text/plain; charset=GBK"));
        assertTrue("GBK 与 UTF-8 解码结果应不同",
                !"中文响应".equals(new String(gbk, Charset.forName("UTF-8"))));
    }

    /**
     * AC-R-21：二进制字节原样进出。
     */
    @Test
    public void shouldKeepBinaryBytes() {
        byte[] binary = new byte[]{0x00, 0x41, (byte) 0xFF};

        assertArrayEquals(binary, HttpCodec.encode(new String(binary, Charset.forName("ISO-8859-1")),
                "application/octet-stream; charset=ISO-8859-1"));
    }

    /**
     * AC-R-15：charset 不可识别 → {@link HttpException}，不静默返回半截字符串。
     */
    @Test
    public void shouldFailOnUnknownCharset() {
        HttpException e = assertThrows(HttpException.class,
                () -> HttpCodec.decode("x".getBytes(Charset.forName("UTF-8")), "text/plain; charset=x-unknown"));

        assertEquals("响应 charset 非法: x-unknown", e.getMessage());
        assertTrue("cause 应为编码异常", e.getCause() != null);
    }

    /**
     * {@code null} 输入返回 {@code null}，不抛异常。
     */
    @Test
    public void shouldPassThroughNull() {
        assertNull(HttpCodec.encode(null, "text/plain"));
        assertNull(HttpCodec.decode(null, "text/plain"));
    }
}
