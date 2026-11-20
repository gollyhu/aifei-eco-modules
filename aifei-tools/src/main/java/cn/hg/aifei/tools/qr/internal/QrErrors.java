/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr.internal;

import cn.hg.aifei.tools.qr.QrException;

/**
 * qr 异常消息与构造的唯一定义点（设计 §7.2 措辞唯一来源）。
 */
public final class QrErrors {

    private QrErrors() {
    }

    /**
     * ZXing 依赖缺失。
     *
     * @param cause 链接错误
     * @return 统一异常
     */
    public static QrException missingDependency(Throwable cause) {
        return new QrException("qr: com.google.zxing:core:3.5.3 is required, add it to your dependencies", cause);
    }

    /**
     * 内容为 null。
     *
     * @return 统一异常
     */
    public static QrException nullText() {
        return new QrException("qr: text must not be null");
    }

    /**
     * 图片字节为空。
     *
     * @return 统一异常
     */
    public static QrException emptyImage() {
        return new QrException("qr: image bytes must not be null or empty");
    }

    /**
     * 输入流为 null。
     *
     * @return 统一异常
     */
    public static QrException nullStream() {
        return new QrException("qr: input stream must not be null");
    }

    /**
     * 参数对象为 null。
     *
     * @param method 方法名
     * @param name 参数名
     * @return 统一异常
     */
    public static QrException nullArgument(String method, String name) {
        return new QrException("qr: " + method + " argument " + name + " must not be null");
    }

    /**
     * 编码失败。
     *
     * @param cause 原异常
     * @return 统一异常
     */
    public static QrException encodeFailed(Throwable cause) {
        return new QrException("qr: failed to encode text, cause: " + detail(cause), cause);
    }

    /**
     * 解码失败。
     *
     * @param cause 原异常
     * @return 统一异常
     */
    public static QrException decodeFailed(Throwable cause) {
        return new QrException("qr: failed to decode image, cause: " + detail(cause), cause);
    }

    private static String detail(Throwable cause) {
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }
}
