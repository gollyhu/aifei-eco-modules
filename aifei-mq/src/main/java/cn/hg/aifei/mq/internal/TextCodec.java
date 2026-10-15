/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.internal;

import java.nio.charset.StandardCharsets;

/**
 * 文本载荷的 UTF-8 编码：本版只提供编码方向，消息不提供解码访问器。
 *
 * <p>编码规则只在本类定义，实现类不得复制。</p>
 */
public final class TextCodec {

    /**
     * 禁止创建工具类实例。
     */
    private TextCodec() {
    }

    /**
     * 把文本编码为 UTF-8 字节。
     *
     * @param text 文本，调用方已保证非 {@code null}
     * @return UTF-8 字节
     */
    public static byte[] encode(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
