/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssException;
import cn.hg.aifei.oss.OssFileInfo;
import cn.hg.aifei.oss.OssObject;
import cn.hg.aifei.oss.OssPage;
import java.util.ArrayList;
import java.util.List;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.get.ListFilesResult;
import org.dromara.x.file.storage.core.get.RemoteDirInfo;
import org.dromara.x.file.storage.core.get.RemoteFileInfo;

/**
 * {@link FileInfo} 到公共类型 {@link OssFileInfo} / {@link OssObject} / {@link OssPage} 的映射<b>唯一定义点</b>。
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssMappers {

    /**
     * 对象大小未知时的回退值。
     */
    private static final long UNKNOWN_SIZE = -1L;

    private OssMappers() {
    }

    /**
     * 取对象字节数，底层未返回大小时回退为 {@code -1}。
     *
     * @param fileInfo 底层文件信息
     * @return 对象字节数；未知时为 {@code -1}
     * @throws OssException {@code fileInfo} 为 {@code null}
     */
    public static long sizeOf(FileInfo fileInfo) {
        if (fileInfo == null) {
            throw new OssException("file info can not be null");
        }
        return (fileInfo.getSize() == null) ? UNKNOWN_SIZE : fileInfo.getSize();
    }

    /**
     * 把底层文件信息映射为公共元数据对象。
     *
     * @param fileInfo 底层文件信息
     * @return 公共元数据对象
     * @throws OssException {@code fileInfo} 为 {@code null}
     */
    public static OssFileInfo toOssFileInfo(FileInfo fileInfo) {
        if (fileInfo == null) {
            throw new OssException("file info can not be null");
        }
        return new OssFileInfo(
                OssPaths.join(fileInfo.getPath(), fileInfo.getFilename()),
                sizeOf(fileInfo),
                fileInfo.getContentType(),
                fileInfo.getUrl(),
                fileInfo.getPlatform(),
                fileInfo.getMetadata()
        );
    }

    /**
     * 把底层文件信息与其下载器映射为公共下载对象。
     *
     * @param fileInfo   底层文件信息
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

    /**
     * 把底层分页列举结果映射为公共分页对象。
     *
     * @param result 底层列举结果，可为 {@code null}
     * @return 公共分页对象
     */
    public static OssPage toPage(ListFilesResult result) {
        List<OssFileInfo> files = new ArrayList<>();
        List<String> directories = new ArrayList<>();
        if (result != null) {
            List<RemoteFileInfo> remoteFiles = result.getFileList();
            if (remoteFiles != null) {
                for (RemoteFileInfo file : remoteFiles) {
                    files.add(toOssFileInfo(file.toFileInfo()));
                }
            }
            List<RemoteDirInfo> remoteDirs = result.getDirList();
            if (remoteDirs != null) {
                for (RemoteDirInfo dir : remoteDirs) {
                    directories.add(OssPaths.join(dir.getPath(), dir.getName()));
                }
            }
        }
        boolean truncated = result != null && Boolean.TRUE.equals(result.getIsTruncated());
        String nextMarker = result == null ? null : result.getNextMarker();
        return new OssPage(files, directories, nextMarker, truncated);
    }
}
