/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.codec;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * codec gunzip 的往返、null/空透传、异常语义与并发测试。
 *
 * <p>压缩样本全部由内存中的 GZIPOutputStream 生成，零外链样本、零联网。</p>
 */
public class CodecUtilTest {

    @Test
    public void gunzip_roundTrip() throws Exception {
        byte[] original = "Hello gzip 世界 \u0000 end".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = gzip(original);
        assertArrayEquals("解压结果须与原文逐字节相等", original, CodecUtil.gunzip(compressed));
    }

    @Test
    public void gunzip_nullAndEmptyPassthrough() {
        assertNull("null 原样返回", CodecUtil.gunzip(null));
        byte[] empty = new byte[0];
        assertSame("空数组原样返回同一实例", empty, CodecUtil.gunzip(empty));
    }

    @Test
    public void gunzip_invalidBytes_readableException() throws Exception {
        assertGunzipFailure("not gzip data".getBytes(StandardCharsets.UTF_8));

        byte[] good = gzip("payload for truncation".getBytes(StandardCharsets.UTF_8));
        assertTrue("样本长度须大于尾部长度", good.length > 8);
        assertGunzipFailure(Arrays.copyOf(good, good.length - 8));

        byte[] corrupt = gzip("payload for crc error".getBytes(StandardCharsets.UTF_8));
        corrupt[corrupt.length / 2] = (byte) (corrupt[corrupt.length / 2] ^ 0x01);
        assertGunzipFailure(corrupt);
    }

    @Test
    public void gunzip_largePayloadAndConcurrent() throws Exception {
        byte[] big = new byte[200000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i % 251);
        }
        byte[] compressed = gzip(big);
        assertArrayEquals("跨缓冲区边界解压须正确", big, CodecUtil.gunzip(compressed));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<String>> futures = new ArrayList<Future<String>>();
        try {
            for (int i = 0; i < 80; i++) {
                final int index = i;
                futures.add(pool.submit(new Callable<String>() {
                    public String call() {
                        byte[] raw = ("item-" + index + "-并发数据").getBytes(StandardCharsets.UTF_8);
                        byte[] decoded;
                        try {
                            decoded = CodecUtil.gunzip(gzip(raw));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                        return new String(decoded, StandardCharsets.UTF_8);
                    }
                }));
            }
            for (int i = 0; i < 80; i++) {
                assertEquals("并发解压串扰: " + i,
                        "item-" + i + "-并发数据", futures.get(i).get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertGunzipFailure(byte[] input) {
        try {
            CodecUtil.gunzip(input);
            fail("非法 gzip 字节须抛 CodecException");
        } catch (CodecException e) {
            assertTrue("须为可读失败消息: " + e.getMessage(),
                    e.getMessage().startsWith("codec: gunzip failed, cause: "));
            assertNotNull("须保留底层根因", e.getCause());
        }
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(out);
        try {
            gzipOut.write(raw);
        } finally {
            gzipOut.close();
        }
        return out.toByteArray();
    }
}
