/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */
package cn.hg.aifei.tools.qr.internal;

/**
 * ZXing 可选依赖探测（唯一探测点）。
 *
 * <p>按设计 §8-A7 采用「方法内延迟符号解析 + 捕获链接错误」，不使用 Class.forName：
 * 只有首次调用本方法才触发内核类解析，缺失时收敛为可读 {@code QrException}。</p>
 */
public final class QrDeps {

    private QrDeps() {
    }

    /**
     * 确保二维码内核在类路径上可用，否则抛可读异常。
     *
     * @throws cn.hg.aifei.tools.qr.QrException 依赖缺失或链接失败
     */
    public static void requireZxing() {
        try {
            Class<?> probe = com.google.zxing.MultiFormatWriter.class;
            if (probe == null) {
                throw new NoClassDefFoundError("MultiFormatWriter");
            }
        } catch (NoClassDefFoundError e) {
            throw QrErrors.missingDependency(e);
        } catch (LinkageError e) {
            throw QrErrors.missingDependency(e);
        }
    }
}
