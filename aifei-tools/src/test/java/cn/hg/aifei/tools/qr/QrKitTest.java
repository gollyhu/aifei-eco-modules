/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr;

import cn.hg.aifei.tools.support.HiddenClassClassLoader;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 二维码门面功能测试。
 *
 * <p>覆盖：encode→decode 闭环、中文 UTF-8 往返、空串往返、四档 EC 与 size/margin 生效、
 * decode(InputStream) 不关闭调用方流、null/空输入抛可读 QrException、decode 失败保留 cause、
 * ZXing 缺失可读异常。</p>
 */
public class QrKitTest {

    @Test
    public void encodeDecode_ascii_roundTrip() {
        String text = "Hello, Aifei Tools 12345";
        assertEquals(text, QrKit.decode(QrKit.encode(text)));
    }

    @Test
    public void encodeDecode_chineseUtf8_roundTrip() {
        String text = "中文测试-二维码-你好世界";
        assertEquals(text, QrKit.decode(QrKit.encode(text)));
    }

    @Test
    public void encodeDecode_emptyString_roundTrip() {
        byte[] png = QrKit.encode("");
        assertNotNull(png);
        assertTrue(png.length > 0);
        assertEquals("", QrKit.decode(png));
    }

    @Test
    public void fourErrorCorrectionLevels_allRoundTrip() {
        String text = "EC-level-test";
        for (QrErrorCorrection ec : QrErrorCorrection.values()) {
            QrOptions opt = QrOptions.of(300, 4, ec);
            assertEquals("纠错档 " + ec + " 往返失败", text, QrKit.decode(QrKit.encode(text, opt)));
        }
    }

    @Test
    public void sizeAndMargin_takeEffect() throws IOException {
        byte[] small = QrKit.encode("size-test", QrOptions.of(150, 2, QrErrorCorrection.M));
        byte[] large = QrKit.encode("size-test", QrOptions.of(400, 6, QrErrorCorrection.M));
        assertEquals(150, ImageIO.read(new ByteArrayInputStream(small)).getWidth());
        assertEquals(400, ImageIO.read(new ByteArrayInputStream(large)).getWidth());
        assertFalse("不同选项应产生不同字节", java.util.Arrays.equals(small, large));
        assertEquals("size-test", QrKit.decode(small));
        assertEquals("size-test", QrKit.decode(large));
    }

    @Test
    public void decodeInputStream_doesNotCloseCallerStream() {
        final byte[] png = QrKit.encode("stream-test");
        final boolean[] closed = {false};
        InputStream in = new ByteArrayInputStream(png) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        assertEquals("stream-test", QrKit.decode(in));
        assertFalse("decode(InputStream) 不得关闭调用方流", closed[0]);
    }

    @Test
    public void encode_nullText_throwsReadable() {
        try {
            QrKit.encode(null);
            fail("encode(null) 应抛 QrException");
        } catch (QrException e) {
            assertEquals("qr: text must not be null", e.getMessage());
        }
    }

    @Test
    public void decode_nullOrEmptyBytes_throwsReadable() {
        try {
            QrKit.decode((byte[]) null);
            fail("decode(null) 应抛 QrException");
        } catch (QrException e) {
            assertEquals("qr: image bytes must not be null or empty", e.getMessage());
        }
        try {
            QrKit.decode(new byte[0]);
            fail("decode(空 byte[]) 应抛 QrException");
        } catch (QrException e) {
            assertEquals("qr: image bytes must not be null or empty", e.getMessage());
        }
        try {
            QrKit.decode((InputStream) null);
            fail("decode((InputStream)null) 应抛 QrException");
        } catch (QrException e) {
            assertEquals("qr: input stream must not be null", e.getMessage());
        }
    }

    @Test
    public void decode_invalidImage_throwsWithCause() {
        try {
            QrKit.decode(new byte[]{1, 2, 3, 4, 5});
            fail("非法图片应抛 QrException");
        } catch (QrException e) {
            assertTrue("消息须含解码失败语义", e.getMessage().contains("qr: failed to decode image"));
            assertNotNull("decode 失败须保留 cause", e.getCause());
        }
    }

    @Test
    public void options_nulls_throwReadable() {
        try {
            QrOptions.of(300, 4, null);
            fail("EC 为 null 应抛 QrException");
        } catch (QrException e) {
            assertEquals("qr: of argument errorCorrection must not be null", e.getMessage());
        }
        try {
            QrKit.encode("x", null);
            fail("options 为 null 应抛 QrException");
        } catch (QrException e) {
            assertEquals("qr: encode argument options must not be null", e.getMessage());
        }
    }

    @Test
    public void zxingMissingDependency_readableException() throws Exception {
        ClassLoader cl = new HiddenClassClassLoader("com.google.zxing.");
        Class<?> deps = Class.forName("cn.hg.aifei.tools.qr.internal.QrDeps", true, cl);
        Method require = deps.getMethod("requireZxing");
        try {
            require.invoke(null);
            fail("ZXing 内核被隐藏时应抛异常");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            assertEquals("cn.hg.aifei.tools.qr.QrException", cause.getClass().getName());
            assertEquals("qr: com.google.zxing:core:3.5.3 is required, add it to your dependencies",
                    cause.getMessage());
            assertNotNull(cause.getCause());
        }
    }
}
