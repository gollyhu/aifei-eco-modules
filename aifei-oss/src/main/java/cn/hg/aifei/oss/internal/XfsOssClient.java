/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssClient;
import cn.hg.aifei.oss.OssFileInfo;
import cn.hg.aifei.oss.OssObject;
import cn.hg.aifei.oss.OssPage;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.constant.Constant;
import org.dromara.x.file.storage.core.get.ListFilesPretreatment;
import org.dromara.x.file.storage.core.get.ListFilesResult;

/**
 * {@link OssClient} 的底层实现：把门面调用翻译为 FileStorageService 调用；非承诺 API。
 *
 * <p>上传固定 {@code saveFilename} 为对象路径中的文件名，使「按路径再次定位」成立
 * （否则底层会生成随机保存名）。{@code basePath} 是平台基路径，按路径定位对象时写入
 * {@link FileInfo#setBasePath(String)}，与底层平台配置保持一致。</p>
 */
public class XfsOssClient implements OssClient {

    private final String platform;
    private final String basePath;
    private final FileStorageService service;

    /**
     * @param platform 本客户端对应的平台标识
     * @param basePath 平台基路径，可为 {@code null}
     * @param service  已构建的服务
     */
    public XfsOssClient(String platform, String basePath, FileStorageService service) {
        this.platform = platform;
        this.basePath = basePath;
        this.service = service;
    }

    @Override
    public OssFileInfo upload(InputStream in, String path, String contentType) {
        try {
            FileInfo info = service.of(in)
                    .setPlatform(platform)
                    .setPath(OssPaths.directory(path))
                    .setSaveFilename(OssPaths.filename(path))
                    .setContentType(contentType)
                    .upload();
            return OssMappers.toOssFileInfo(info);
        } catch (RuntimeException e) {
            throw OssErrors.translate("upload", platform, path, e);
        }
    }

    @Override
    public OssFileInfo upload(byte[] data, String path, String contentType) {
        try {
            FileInfo info = service.of(data)
                    .setPlatform(platform)
                    .setPath(OssPaths.directory(path))
                    .setSaveFilename(OssPaths.filename(path))
                    .setContentType(contentType)
                    .upload();
            return OssMappers.toOssFileInfo(info);
        } catch (RuntimeException e) {
            throw OssErrors.translate("upload", platform, path, e);
        }
    }

    @Override
    public OssFileInfo uploadIfAbsent(InputStream in, String path, String contentType) {
        if (exists(path)) {
            return getFileInfo(path);
        }
        return upload(in, path, contentType);
    }

    @Override
    public OssFileInfo uploadIfAbsent(byte[] data, String path, String contentType) {
        if (exists(path)) {
            return getFileInfo(path);
        }
        return upload(data, path, contentType);
    }

    @Override
    public OssObject download(String path) {
        try {
            FileInfo info = OssPaths.toFileInfo(platform, basePath, path);
            return OssMappers.toObject(info, service.download(info));
        } catch (RuntimeException e) {
            throw OssErrors.translate("download", platform, path, e);
        }
    }

    @Override
    public boolean delete(String path) {
        try {
            return service.delete(OssPaths.toFileInfo(platform, basePath, path));
        } catch (RuntimeException e) {
            throw OssErrors.translate("delete", platform, path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return service.exists(OssPaths.toFileInfo(platform, basePath, path));
        } catch (RuntimeException e) {
            throw OssErrors.translate("exists", platform, path, e);
        }
    }

    @Override
    public OssFileInfo getFileInfo(String path) {
        try {
            FileInfo info = service.getFile(OssPaths.toFileInfo(platform, basePath, path)).toFileInfo();
            return OssMappers.toOssFileInfo(info);
        } catch (RuntimeException e) {
            throw OssErrors.translate("get file info", platform, path, e);
        }
    }

    @Override
    public List<OssFileInfo> listFiles(String path) {
        return listFiles(path, 0, null).getFiles();
    }

    @Override
    public OssPage listFiles(String path, int maxFiles, String marker) {
        try {
            ListFilesPretreatment pretreatment = service.listFiles()
                    .setPlatform(true, platform)
                    .setPath(true, OssPaths.asDirectory(path));
            if (maxFiles > 0) {
                pretreatment.setMaxFiles(true, maxFiles);
            }
            if (marker != null && !marker.isEmpty()) {
                pretreatment.setMarker(true, marker);
            }
            ListFilesResult result = pretreatment.listFiles();
            return OssMappers.toPage(result);
        } catch (RuntimeException e) {
            throw OssErrors.translate("list files", platform, path, e);
        }
    }

    @Override
    public String generatePresignedUrl(String path, long ttlSeconds) {
        try {
            FileInfo info = OssPaths.toFileInfo(platform, basePath, path);
            Date expiration = new Date(System.currentTimeMillis() + ttlSeconds * 1000L);
            return service.generatePresignedUrl(info, expiration);
        } catch (RuntimeException e) {
            throw OssErrors.translate("generate presigned url", platform, path, e);
        }
    }

    @Override
    public boolean copy(String sourcePath, String destPath) {
        try {
            FileInfo source = OssPaths.toFileInfo(platform, basePath, sourcePath);
            FileInfo copied = service.copy(source)
                    .setCopyMode(true, Constant.CopyMode.AUTO)
                    .setPlatform(true, platform)
                    .setPath(true, OssPaths.directory(destPath))
                    .setFilename(true, OssPaths.filename(destPath))
                    .copy();
            return copied != null;
        } catch (RuntimeException e) {
            throw OssErrors.translate("copy", platform, sourcePath + " -> " + destPath, e);
        }
    }

    @Override
    public boolean copyTo(String destPlatform, String sourcePath, String destPath) {
        try {
            FileInfo source = OssPaths.toFileInfo(platform, basePath, sourcePath);
            FileInfo copied = service.copy(source)
                    .setCopyMode(true, Constant.CopyMode.CROSS)
                    .setPlatform(true, destPlatform)
                    .setPath(true, OssPaths.directory(destPath))
                    .setFilename(true, OssPaths.filename(destPath))
                    .copy();
            return copied != null;
        } catch (RuntimeException e) {
            throw OssErrors.translate("copy cross platform", platform,
                    sourcePath + " -> " + destPlatform + ":" + destPath, e);
        }
    }
}
