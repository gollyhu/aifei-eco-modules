/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.codec.internal;

import cn.hg.aifei.tools.codec.CodecException;

/**
 * codec 异常消息与异常构造的唯一定义点。
 */
public final class CodecErrors {

    private CodecErrors() {
    }

    /**
     * 解压失败，保留底层根因。
     *
     * @param cause 根因
     * @return 统一异常
     */
    public static CodecException gunzipFailed(Throwable cause) {
        return new CodecException("codec: gunzip failed, cause: " + detail(cause), cause);
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
