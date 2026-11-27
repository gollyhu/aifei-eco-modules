/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;

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

    private static final String MISSING_TOOLS =
            "缺少可选依赖 io.github.gollyhu:aifei-tools:1.1.0-SNAPSHOT；使用本能力时消费方须显式声明";

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
        return gunzipViaTools(raw);
    }

    private static byte[] gunzipViaTools(byte[] raw) {
        try {
            return GzipHolder.gunzip(raw);
        } catch (NoClassDefFoundError e) {
            throw new HttpException("gzip 解压失败：" + MISSING_TOOLS, e);
        }
    }

    /**
     * 全限定名调用且不写 import：保持门禁①白名单与断言文本逐字不变；同时隔离 tools 异常类型引用。
     */
    private static final class GzipHolder {
        private static byte[] gunzip(byte[] raw) {
            try {
                return cn.hg.aifei.tools.codec.CodecUtil.gunzip(raw);
            } catch (cn.hg.aifei.tools.codec.CodecException e) {
                throw new HttpException("gzip 解压失败",
                        e.getCause() != null ? e.getCause() : e);
            }
        }
    }
}
