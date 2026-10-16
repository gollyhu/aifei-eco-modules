/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssException;
import cn.hg.aifei.oss.OssFileInfo;
import cn.hg.aifei.oss.OssObject;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;

/**
 * {@link FileInfo} 到公共类型 {@link OssFileInfo} / {@link OssObject} 的映射<b>唯一定义点</b>。
 *
 * <p>把底层的文件信息收敛为本模块自有值对象，公共 API 因此不暴露任何第三方类型。对象路径的拼回口径与
 * {@link OssPaths} 保持一致，不在此另行切分 / 拼接规则。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssMappers {

    /**
     * 对象大小未知时的回退值。
     */
    private static final long UNKNOWN_SIZE = -1L;

    /**
     * 禁止创建工具类实例。
     */
    private OssMappers() {
    }

    /**
     * 把底层文件信息映射为公共元数据对象。
     *
     * <p>对象路径由 {@code path}（目录段）与 {@code filename}（末段）拼回；大小未知时以 {@code -1} 表示。</p>
     *
     * @param fileInfo 底层文件信息
     * @return 公共元数据对象
     * @throws OssException {@code fileInfo} 为 {@code null}
     */
    public static OssFileInfo toOssFileInfo(FileInfo fileInfo) {
        if (fileInfo == null) {
            throw new OssException("file info can not be null");
        }
        String path = OssPaths.join(fileInfo.getPath(), fileInfo.getFilename());
        long size = (fileInfo.getSize() == null) ? UNKNOWN_SIZE : fileInfo.getSize();
        return new OssFileInfo(
                path,
                size,
                fileInfo.getContentType(),
                fileInfo.getUrl(),
                fileInfo.getPlatform(),
                fileInfo.getMetadata()
        );
    }

    /**
     * 把底层文件信息与其下载器映射为公共下载对象。
     *
     * @param fileInfo 底层文件信息
     * @param downloader 底层下载器
     * @return 公共下载对象；内容按次读取，无需关闭
     * @throws OssException {@code fileInfo} 或 {@code downloader} 为 {@code null}
     */
    public static OssObject toObject(FileInfo fileInfo, Downloader downloader) {
        if (fileInfo == null) {
            throw new OssException("file info can not be null");
        }
        if (downloader == null) {
            throw new OssException("downloader can not be null");
        }
        return new XfsOssObject(fileInfo, downloader);
    }
}
