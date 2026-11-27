/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.codec.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * gzip 解压的唯一实现点，只使用局部流与缓冲区，可并发调用。
 */
public final class GzipEngine {

    private static final int BUFFER_SIZE = 8192;

    private GzipEngine() {
    }

    /**
     * 解压 gzip 字节；null 与空数组按原样返回。
     *
     * @param raw 待解压字节，允许为 null 或空数组
     * @return 解压结果，或原样返回的入参
     */
    public static byte[] gunzip(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return raw;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
        } catch (IOException e) {
            throw CodecErrors.gunzipFailed(e);
        }
        return out.toByteArray();
    }
}
