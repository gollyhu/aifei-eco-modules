/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr;

/**
 * 二维码纠错等级，值越高抗遮挡能力越强、容量越低。
 */
public enum QrErrorCorrection {

    /** 约 7% 码字可恢复。 */
    L,

    /** 约 15% 码字可恢复，为默认等级。 */
    M,

    /** 约 25% 码字可恢复。 */
    Q,

    /** 约 30% 码字可恢复。 */
    H
}
