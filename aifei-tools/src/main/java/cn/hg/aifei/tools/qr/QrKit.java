/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr;

import java.io.InputStream;

import cn.hg.aifei.tools.qr.internal.ImageCodec;
import cn.hg.aifei.tools.qr.internal.QrDeps;
import cn.hg.aifei.tools.qr.internal.QrErrors;

/**
 * 二维码全静态门面：文本与 PNG 字节互转。
 *
 * <p>内核依赖为可选，首次调用才探测，缺失时抛可读 {@link QrException}；
 * 空串是合法内容，可正常编码并还原。</p>
 */
public final class QrKit {

    private QrKit() {
    }

    /**
     * 以默认选项编码文本为 PNG。
     *
     * @param text 待编码文本，可为空串
     * @return PNG 字节
     * @throws QrException 文本为 null、依赖缺失或编码失败
     */
    public static byte[] encode(String text) {
        return encode(text, QrOptions.defaults());
    }

    /**
     * 以指定选项编码文本为 PNG。
     *
     * @param text 待编码文本，可为空串
     * @param options 编码选项
     * @return PNG 字节
     * @throws QrException 参数非法、依赖缺失或编码失败
     */
    public static byte[] encode(String text, QrOptions options) {
        if (text == null) {
            throw QrErrors.nullText();
        }
        if (options == null) {
            throw QrErrors.nullArgument("encode", "options");
        }
        QrDeps.requireZxing();
        return ImageCodec.encodePng(text, options.getSize(), options.getMargin(), options.getErrorCorrection());
    }

    /**
     * 从 PNG 字节还原文本。
     *
     * @param imageBytes PNG 字节，不得为 null 或空数组
     * @return 原始文本，可为空串
     * @throws QrException 输入为空、依赖缺失或无法识别
     */
    public static String decode(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw QrErrors.emptyImage();
        }
        QrDeps.requireZxing();
        return ImageCodec.decodePng(imageBytes);
    }

    /**
     * 从输入流还原文本；不关闭调用方传入的流。
     *
     * @param in PNG 输入流
     * @return 原始文本，可为空串
     * @throws QrException 输入为空、依赖缺失或无法识别
     */
    public static String decode(InputStream in) {
        if (in == null) {
            throw QrErrors.nullStream();
        }
        QrDeps.requireZxing();
        return ImageCodec.decodeStream(in);
    }
}
