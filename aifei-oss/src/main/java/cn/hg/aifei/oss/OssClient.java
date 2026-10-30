/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储门面（对应默认平台或指定平台）。
 *
 * <p>由 {@code OssPlugin} 装配为单例，经 {@code @Inject} 取用（默认平台），
 * 或经 {@link OssKit#use(String)} 按平台取用。全部方法线程安全。
 */
public interface OssClient {

    /**
     * 上传流。
     *
     * @param in          内容流（由本方法负责读取）
     * @param path        对象路径（如 {@code upload/2026/a.png}）
     * @param contentType 内容类型，可为 {@code null}
     * @return 上传后的对象元数据
     * @throws OssException 上传失败时抛出
     */
    OssFileInfo upload(InputStream in, String path, String contentType);

    /**
     * 上传字节数组。
     *
     * @param data        内容
     * @param path        对象路径
     * @param contentType 内容类型，可为 {@code null}
     * @return 上传后的对象元数据
     * @throws OssException 上传失败时抛出
     */
    OssFileInfo upload(byte[] data, String path, String contentType);

    /**
     * 仅当对象不存在时上传（非原子操作：并发下可能被后写覆盖）。
     *
     * @param in          内容流
     * @param path        对象路径
     * @param contentType 内容类型，可为 {@code null}
     * @return 上传后（或已存在）的对象元数据
     * @throws OssException 上传失败时抛出
     */
    OssFileInfo uploadIfAbsent(InputStream in, String path, String contentType);

    /**
     * 仅当对象不存在时上传（非原子操作）。
     *
     * @param data        内容
     * @param path        对象路径
     * @param contentType 内容类型，可为 {@code null}
     * @return 上传后（或已存在）的对象元数据
     * @throws OssException 上传失败时抛出
     */
    OssFileInfo uploadIfAbsent(byte[] data, String path, String contentType);

    /**
     * 下载对象（惰性：读取内容时才访问存储）。
     *
     * @param path 对象路径
     * @return 下载结果（内容按需读取）
     * @throws OssException 读取时对象不存在或失败抛出
     */
    OssObject download(String path);

    /**
     * 删除对象（幂等：不存在也返回 {@code true}）。
     *
     * @param path 对象路径
     * @return 删除成功返回 {@code true}
     * @throws OssException 删除失败时抛出
     */
    boolean delete(String path);

    /**
     * 判断对象是否存在。
     *
     * @param path 对象路径
     * @return 存在返回 {@code true}
     * @throws OssException 校验失败时抛出
     */
    boolean exists(String path);

    /**
     * 查询对象元数据。
     *
     * @param path 对象路径
     * @return 对象元数据
     * @throws OssException 对象不存在或查询失败时抛出
     */
    OssFileInfo getFileInfo(String path);

    /**
     * 列举目录下的全部对象（不递归、一次性返回）。
     *
     * @param path 目录路径（尾斜杠可省略）
     * @return 对象元数据列表（可能为空）
     * @throws OssException 平台不支持列举或列举失败时抛出
     */
    List<OssFileInfo> listFiles(String path);

    /**
     * 分页列举目录下的对象与子目录（不递归）。
     *
     * @param path     目录路径
     * @param maxFiles 本页最多返回的对象数；{@code <= 0} 表示由平台默认
     * @param marker   上一页返回的 {@link OssPage#getNextMarker()}；首页传 {@code null}
     * @return 分页结果（含对象、子目录、下一页游标与是否截断）
     * @throws OssException 平台不支持列举或列举失败时抛出
     */
    OssPage listFiles(String path, int maxFiles, String marker);

    /**
     * 生成限时访问地址。
     *
     * @param path       对象路径
     * @param ttlSeconds 有效期（秒）
     * @return 限时访问地址
     * @throws OssException 平台不支持或生成失败时抛出
     */
    String generatePresignedUrl(String path, long ttlSeconds);

    /**
     * 同平台内复制（服务端复制优先，不支持时降级）。
     *
     * @param sourcePath 源对象路径
     * @param destPath   目标对象路径
     * @return 复制成功返回 {@code true}
     * @throws OssException 复制失败时抛出
     */
    boolean copy(String sourcePath, String destPath);

    /**
     * 跨平台复制（{@code CopyMode.CROSS}：下载源对象再上传到目标平台）。
     *
     * @param destPlatform 目标平台标识（须已装配）
     * @param sourcePath   源对象路径（本客户端所属平台）
     * @param destPath     目标对象路径（目标平台）
     * @return 复制成功返回 {@code true}
     * @throws OssException 目标平台未知或复制失败时抛出
     */
    boolean copyTo(String destPlatform, String sourcePath, String destPath);
}
