/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssClient;
import cn.hg.aifei.oss.OssException;
import cn.hg.aifei.oss.OssFileInfo;
import cn.hg.aifei.oss.OssObject;
import java.io.InputStream;
import java.util.Date;
import org.dromara.x.file.storage.core.Downloader;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.get.RemoteFileInfo;

/**
 * {@link OssClient} 的存储实现：把一个存储平台的全部门面调用翻译为底层文件存储服务的调用。
 *
 * <p>只使用 {@link FileInfo} 一族 API 定位对象——依据 {@link OssPaths} 的换算口径自行构造 {@code FileInfo}
 * 后调用存在性 / 删除 / 下载 / 获取文件，<b>不</b>使用依赖文件记录器、且默认记录器恒抛异常的 String url 一族
 * API。</p>
 *
 * <p>上传以 {@code setPath(dir).setSaveFilename(filename)} 写入，使「按路径再次定位」成立；{@code copy} 本期
 * 未接线，调用即抛 {@link OssException}。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class XfsOssClient implements OssClient {

    /**
     * 所属存储平台标识。
     */
    private final String platform;

    /**
     * 平台配置的 {@code base-path}，参与物理 key 的拼装。
     */
    private final String basePath;

    /**
     * 已装配的底层文件存储服务。
     */
    private final FileStorageService service;

    /**
     * 创建一个平台客户端。
     *
     * @param platform 平台标识，非空白
     * @param basePath 平台配置的 {@code base-path}，可为 {@code null}
     * @param service 已装配的文件存储服务
     * @throws OssException {@code platform} 为空白，或 {@code service} 为 {@code null}
     */
    public XfsOssClient(String platform, String basePath, FileStorageService service) {
        if (platform == null || platform.trim().isEmpty()) {
            throw new OssException("oss platform can not be blank");
        }
        if (service == null) {
            throw new OssException("file storage service can not be null");
        }
        this.platform = platform;
        this.basePath = basePath;
        this.service = service;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OssFileInfo upload(InputStream in, String path, String contentType) {
        if (in == null) {
            throw new OssException("input stream can not be null");
        }
        String dir = OssPaths.directoryOf(path);
        String filename = OssPaths.filenameOf(path);
        try {
            FileInfo fileInfo = service.of(in, filename, contentType)
                    .setPlatform(platform)
                    .setPath(dir)
                    .setSaveFilename(filename)
                    .upload();
            if (fileInfo == null) {
                throw new OssException("upload returned no file info, platform: " + platform + ", path: " + path);
            }
            return OssMappers.toOssFileInfo(fileInfo);
        } catch (RuntimeException e) {
            throw OssErrors.translate("upload", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OssFileInfo upload(byte[] data, String path, String contentType) {
        if (data == null) {
            throw new OssException("data can not be null");
        }
        String dir = OssPaths.directoryOf(path);
        String filename = OssPaths.filenameOf(path);
        try {
            FileInfo fileInfo = service.of(data, filename, contentType)
                    .setPlatform(platform)
                    .setPath(dir)
                    .setSaveFilename(filename)
                    .upload();
            if (fileInfo == null) {
                throw new OssException("upload returned no file info, platform: " + platform + ", path: " + path);
            }
            return OssMappers.toOssFileInfo(fileInfo);
        } catch (RuntimeException e) {
            throw OssErrors.translate("upload", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OssObject download(String path) {
        FileInfo fileInfo = OssPaths.toFileInfo(platform, basePath, path);
        try {
            RemoteFileInfo remote = service.getFile(fileInfo);
            if (remote == null) {
                throw new OssException("object not found, platform: " + platform + ", path: " + path);
            }
            FileInfo resolved = remote.toFileInfo(fileInfo);
            Downloader downloader = service.download(resolved);
            return OssMappers.toObject(resolved, downloader);
        } catch (RuntimeException e) {
            throw OssErrors.translate("download", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean delete(String path) {
        FileInfo fileInfo = OssPaths.toFileInfo(platform, basePath, path);
        try {
            if (!service.exists(fileInfo)) {
                return false;
            }
            return service.delete(fileInfo);
        } catch (RuntimeException e) {
            throw OssErrors.translate("delete", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(String path) {
        FileInfo fileInfo = OssPaths.toFileInfo(platform, basePath, path);
        try {
            return service.exists(fileInfo);
        } catch (RuntimeException e) {
            throw OssErrors.translate("exists", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OssFileInfo getFileInfo(String path) {
        FileInfo fileInfo = OssPaths.toFileInfo(platform, basePath, path);
        try {
            RemoteFileInfo remote = service.getFile(fileInfo);
            if (remote == null) {
                throw new OssException("object not found, platform: " + platform + ", path: " + path);
            }
            return OssMappers.toOssFileInfo(remote.toFileInfo(fileInfo));
        } catch (RuntimeException e) {
            throw OssErrors.translate("get file info", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String generatePresignedUrl(String path, long ttlSeconds) {
        FileInfo fileInfo = OssPaths.toFileInfo(platform, basePath, path);
        try {
            if (!service.isSupportPresignedUrl(platform)) {
                throw new OssException("platform does not support presigned url: " + platform);
            }
            String url = service.generatePresignedUrl(fileInfo, new Date(System.currentTimeMillis() + ttlSeconds * 1000L));
            if (url == null) {
                throw new OssException(
                        "platform returned no presigned url, platform: " + platform + ", path: " + path);
            }
            return url;
        } catch (RuntimeException e) {
            throw OssErrors.translate("generate presigned url", platform, path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean copy(String sourcePath, String destPath) {
        throw new OssException(
                "same-platform copy is not wired yet, platform: " + platform
                        + ", source: " + sourcePath + ", dest: " + destPath);
    }
}
