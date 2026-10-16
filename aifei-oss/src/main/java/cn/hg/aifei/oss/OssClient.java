/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import java.io.InputStream;

/**
 * 对象存储门面接口，一个实例对应一个存储平台。
 *
 * <p><b>对象路径</b>（{@code path}）是平台 {@code base-path} 之下的完整逻辑对象 key，形如
 * {@code upload/2026/a.png}：不得为空、不得以 {@code /} 开头、不得含 {@code ..}、末段不得为空。它既是上传
 * 的目标路径，也是下载 / 删除 / 存在性判断 / 查询 / 签名所依据的定位路径，可无损往返。</p>
 *
 * <p><b>平台选择</b>：{@code @Inject OssClient} 对应默认平台；多平台经 {@link OssKit#use(String)} 按平台
 * 标识取用。</p>
 *
 * <p><b>线程安全</b>：全部方法线程安全，实例可长期持有并并发使用。</p>
 *
 * <p><b>异常</b>：全部失败场景统一抛 {@link OssException}（如路径非法、对象不存在、平台不支持、底层平台
 * 故障），异常消息中不含任何第三方类型名。</p>
 */
public interface OssClient {

    /**
     * 上传字节流。
     *
     * <p>同名路径跟随底层存储平台的默认覆盖语义。{@code in} 由调用方负责关闭，本方法不关闭入参。</p>
     *
     * @param in 字节流
     * @param path 对象路径
     * @param contentType 对象 MIME 类型，可为 {@code null}（由实现按文件名识别）
     * @return 上传后对象的元数据
     * @throws OssException 路径非法或上传失败
     */
    OssFileInfo upload(InputStream in, String path, String contentType);

    /**
     * 上传字节数组。
     *
     * <p>同名路径跟随底层存储平台的默认覆盖语义。</p>
     *
     * @param data 字节数组
     * @param path 对象路径
     * @param contentType 对象 MIME 类型，可为 {@code null}（由实现按文件名识别）
     * @return 上传后对象的元数据
     * @throws OssException 路径非法或上传失败
     */
    OssFileInfo upload(byte[] data, String path, String contentType);

    /**
     * 下载对象。
     *
     * @param path 对象路径
     * @return 对象内容与元数据；返回的 {@link OssObject} 无需关闭，内容按次读取
     * @throws OssException 路径非法、对象不存在或下载失败
     */
    OssObject download(String path);

    /**
     * 删除对象。
     *
     * @param path 对象路径
     * @return {@code true}：对象存在并已删除；{@code false}：对象本就不存在
     * @throws OssException 路径非法或删除失败
     */
    boolean delete(String path);

    /**
     * 判断对象是否存在。
     *
     * @param path 对象路径
     * @return {@code true} 表示对象存在
     * @throws OssException 路径非法或判断失败
     */
    boolean exists(String path);

    /**
     * 查询对象元数据。
     *
     * @param path 对象路径
     * @return 对象元数据
     * @throws OssException 路径非法、对象不存在或查询失败
     */
    OssFileInfo getFileInfo(String path);

    /**
     * 生成限时访问 URL。
     *
     * <p>可用性取决于平台能力：平台不支持签名 URL 时抛 {@link OssException}。</p>
     *
     * @param path 对象路径
     * @param ttlSeconds 有效时长，单位秒
     * @return 限时访问 URL
     * @throws OssException 路径非法、平台不支持或生成失败
     */
    String generatePresignedUrl(String path, long ttlSeconds);

    /**
     * 同平台内复制对象。
     *
     * <p><b>本期未接线</b>：调用即抛 {@link OssException}。跨平台复制不在本期范围。</p>
     *
     * @param sourcePath 源对象路径
     * @param destPath 目标对象路径
     * @return 复制是否成功
     * @throws OssException 本期固定抛出，说明同平台复制尚未支持
     */
    boolean copy(String sourcePath, String destPath);
}
