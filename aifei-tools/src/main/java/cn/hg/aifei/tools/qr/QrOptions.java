/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr;

import cn.hg.aifei.tools.qr.internal.QrErrors;

/**
 * 二维码生成选项值对象；字符集固定 UTF-8，不对外暴露。
 */
public final class QrOptions {

    private static final int DEFAULT_SIZE = 300;
    private static final int DEFAULT_MARGIN = 4;

    private final int size;
    private final int margin;
    private final QrErrorCorrection errorCorrection;

    private QrOptions(int size, int margin, QrErrorCorrection errorCorrection) {
        this.size = size;
        this.margin = margin;
        this.errorCorrection = errorCorrection;
    }

    /**
     * 默认选项：边长 300 像素、边距 4、纠错等级 M。
     *
     * @return 默认选项
     */
    public static QrOptions defaults() {
        return new QrOptions(DEFAULT_SIZE, DEFAULT_MARGIN, QrErrorCorrection.M);
    }

    /**
     * 自定义选项。
     *
     * @param size 图片边长像素
     * @param margin 静区边距
     * @param errorCorrection 纠错等级
     * @return 自定义选项
     * @throws QrException 纠错等级为 null
     */
    public static QrOptions of(int size, int margin, QrErrorCorrection errorCorrection) {
        if (errorCorrection == null) {
            throw QrErrors.nullArgument("of", "errorCorrection");
        }
        return new QrOptions(size, margin, errorCorrection);
    }

    /**
     * 图片边长像素。
     *
     * @return 边长
     */
    public int getSize() {
        return size;
    }

    /**
     * 静区边距。
     *
     * @return 边距
     */
    public int getMargin() {
        return margin;
    }

    /**
     * 纠错等级。
     *
     * @return 纠错等级
     */
    public QrErrorCorrection getErrorCorrection() {
        return errorCorrection;
    }
}
