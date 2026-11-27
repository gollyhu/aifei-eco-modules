/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.codec;

import cn.hg.aifei.tools.codec.internal.GzipEngine;

/**
 * gzip 解压的无状态工具，仅提供解压一个静态方法。
 *
 * <p>是否传入压缩字节由调用方决定，本类不读取任何配置、不干涉传输层协商；
 * 失败统一抛 {@link CodecException}，公共方法不声明受检异常，可多线程并发调用。</p>
 */
public final class CodecUtil {

    private CodecUtil() {
    }

    /**
     * 解压 gzip 格式的字节数组。
     *
     * @param raw 待解压字节，允许为 null 或空数组，此时原样返回
     * @return 解压后的字节；入参为 null 返回 null，入参为空数组返回该空数组
     * @throws CodecException 字节不是合法 gzip 数据、流被截断或校验失败
     */
    public static byte[] gunzip(byte[] raw) {
        return GzipEngine.gunzip(raw);
    }
}
