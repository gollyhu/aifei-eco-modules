/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * gzip 支持：{@code Accept-Encoding} 决策与解压只在本类定义。
 *
 * <p>默认关闭——不发送 {@code Accept-Encoding}、也不解压（服务端硬编码返回 gzip 时原样交出字节）；
 * 仅 {@code http.gzip=true} 才发送并在模块内解压，解压失败抛 {@link HttpException}。</p>
 */
public final class HttpGzip {

    /**
     * 解压缓冲区大小。
     */
    private static final int BUFFER_SIZE = 8192;

    /**
     * 禁止创建工具类实例。
     */
    private HttpGzip() {
    }

    /**
     * 判断是否接受 gzip 响应（决定是否发送 {@code Accept-Encoding: gzip}）。
     *
     * @param config 配置，为 {@code null} 时视为关闭
     * @return 接受时 {@code true}
     */
    public static boolean acceptEncoding(HttpConfig config) {
        return config != null && config.gzip();
    }

    /**
     * 解压 gzip 字节。
     *
     * @param raw 压缩字节
     * @return 解压后的字节；{@code raw} 为 {@code null} 或空时原样返回
     * @throws HttpException 解压失败
     */
    public static byte[] decode(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return raw;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(raw.length * 2, BUFFER_SIZE));
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new HttpException("gzip 解压失败", e);
        }
        return out.toByteArray();
    }
}
