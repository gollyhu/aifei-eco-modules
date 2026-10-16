/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 上传或查询返回的对象元数据，是适配层把 {@code FileInfo} 收敛后的自有值对象。
 *
 * <p>本类为不可变值对象：字段只读，{@link #getMetadata()} 返回不可修改的副本，可安全地在多线程间传递。
 * 对象路径 {@link #getPath()} 的语义见 {@code OssClient}：它是平台 {@code base-path} 之下的完整逻辑对象
 * key，可直接用于下载 / 删除 / 存在性判断。</p>
 */
public final class OssFileInfo {

    /**
     * 对象路径（平台 {@code base-path} 之下的完整逻辑对象 key）。
     */
    private final String path;

    /**
     * 对象大小，单位字节。
     */
    private final long size;

    /**
     * 对象 MIME 类型；平台无法识别时为 {@code null}。
     */
    private final String contentType;

    /**
     * 对象访问地址；平台可生成时为该地址，否则为 {@code null}。
     */
    private final String url;

    /**
     * 所属存储平台标识。
     */
    private final String platform;

    /**
     * 对象元数据；永不为 {@code null}，无元数据时为空 Map。
     */
    private final Map<String, String> metadata;

    /**
     * 创建对象元数据。
     *
     * @param path 对象路径
     * @param size 对象大小，单位字节
     * @param contentType 对象 MIME 类型，可为 {@code null}
     * @param url 对象访问地址，可为 {@code null}
     * @param platform 所属存储平台标识
     * @param metadata 对象元数据，可为 {@code null}（视为空）
     */
    public OssFileInfo(String path, long size, String contentType, String url,
                       String platform, Map<String, String> metadata) {
        this.path = path;
        this.size = size;
        this.contentType = contentType;
        this.url = url;
        this.platform = platform;
        this.metadata = (metadata == null || metadata.isEmpty())
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * 返回对象路径。
     *
     * @return 平台 {@code base-path} 之下的完整逻辑对象 key
     */
    public String getPath() {
        return path;
    }

    /**
     * 返回对象大小。
     *
     * @return 字节数
     */
    public long getSize() {
        return size;
    }

    /**
     * 返回对象 MIME 类型。
     *
     * @return MIME 类型；平台无法识别时为 {@code null}
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 返回对象访问地址。
     *
     * @return 访问地址；平台可生成时为该地址，否则为 {@code null}
     */
    public String getUrl() {
        return url;
    }

    /**
     * 返回所属存储平台标识。
     *
     * @return 存储平台标识
     */
    public String getPlatform() {
        return platform;
    }

    /**
     * 返回对象元数据。
     *
     * @return 不可修改的元数据 Map；无元数据时为空 Map
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * 返回对象元数据的可读描述。
     *
     * @return 描述字符串
     */
    @Override
    public String toString() {
        return "OssFileInfo{path=" + path
                + ", size=" + size
                + ", contentType=" + contentType
                + ", url=" + url
                + ", platform=" + platform
                + ", metadata=" + metadata + '}';
    }
}
