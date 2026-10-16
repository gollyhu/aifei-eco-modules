/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssException;
import cn.hg.aifei.oss.OssObject;
import java.io.OutputStream;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;

/**
 * {@link OssObject} 的存储实现：<b>回调式下载</b>，不持有输入流。
 *
 * <p>持有一个底层下载器而非流对象：每次 {@link #bytes()} / {@link #writeTo(OutputStream)} 都会经下载器重新
 * 向存储平台发起一次读取，流在底层回调内开启并在读取结束后关闭，因此本对象无需关闭、可重复读取。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class XfsOssObject implements OssObject {

    /**
     * 对象大小未知时的回退值。
     */
    private static final long UNKNOWN_SIZE = -1L;

    /**
     * 完整逻辑对象路径。
     */
    private final String path;

    /**
     * 对象大小，单位字节；未知时为 {@code -1}。
     */
    private final long contentLength;

    /**
     * 对象 MIME 类型；未知时为 {@code null}。
     */
    private final String contentType;

    /**
     * 所属存储平台标识，仅用于异常消息。
     */
    private final String platform;

    /**
     * 底层下载器；按次读取，不缓存流。
     */
    private final Downloader downloader;

    /**
     * 由底层文件信息与下载器创建对象。
     *
     * @param fileInfo 底层文件信息（提供路径、大小、类型与平台）
     * @param downloader 底层下载器
     */
    public XfsOssObject(FileInfo fileInfo, Downloader downloader) {
        this.path = OssPaths.join(fileInfo.getPath(), fileInfo.getFilename());
        this.contentLength = (fileInfo.getSize() == null) ? UNKNOWN_SIZE : fileInfo.getSize();
        this.contentType = fileInfo.getContentType();
        this.platform = fileInfo.getPlatform();
        this.downloader = downloader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPath() {
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getContentLength() {
        return contentLength;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] bytes() {
        try {
            byte[] data = downloader.bytes();
            return (data == null) ? new byte[0] : data;
        } catch (RuntimeException e) {
            throw OssErrors.translate("read object", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeTo(OutputStream out) {
        if (out == null) {
            throw new OssException("output stream can not be null");
        }
        try {
            downloader.outputStream(out);
        } catch (RuntimeException e) {
            throw OssErrors.translate("write object", platform, path, e);
        }
    }
}
