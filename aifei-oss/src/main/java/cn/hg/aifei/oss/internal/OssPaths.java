/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssException;
import org.dromara.x.file.storage.core.FileInfo;

/**
 * 对象路径与底层 {@link FileInfo} 之间的换算；非承诺 API。
 *
 * <p>本模块以「完整对象路径」为对外语义（如 {@code upload/2026/a.png}），
 * 底层以「基路径 {@code basePath} + 目录 {@code path} + 文件名 {@code filename}」表达，
 * 换算规则集中在本类。
 */
final class OssPaths {

    /**
     * @param path 完整对象路径
     * @return 目录部分（含结尾 {@code /}），无目录时返回空串
     */
    static String directory(String path) {
        if (path == null) {
            throw new OssException("对象路径不能为 null");
        }
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx + 1);
    }

    /**
     * 把目录路径规范化为带结尾 {@code /} 的形式（用于列举）。
     *
     * @param path 目录路径，可为 {@code null}
     * @return 规范化目录；{@code path} 为 {@code null} 或空串时返回空串
     */
    static String asDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return path.endsWith("/") ? path : path + "/";
    }

    /**
     * @param path 完整对象路径
     * @return 文件名部分
     * @throws OssException 路径缺少文件名时抛出
     */
    static String filename(String path) {
        String name = directory(path).isEmpty() ? path : path.substring(path.lastIndexOf('/') + 1);
        if (name == null || name.isEmpty()) {
            throw new OssException("对象路径缺少文件名: " + path);
        }
        return name;
    }

    /**
     * 把目录段与文件名段拼回完整对象路径；路径拼接口径的唯一定义点。
     *
     * @param path     目录段，可为 {@code null}
     * @param filename 文件名段，可为 {@code null}
     * @return 完整对象路径
     */
    static String join(String path, String filename) {
        String dir = path == null ? "" : path;
        String name = filename == null ? "" : filename;
        return dir + name;
    }

    /**
     * 由平台、基路径与完整对象路径构造底层 {@link FileInfo}（用于按路径定位既有对象）。
     *
     * @param platform 平台标识
     * @param basePath 平台基路径，可为 {@code null}
     * @param path     完整对象路径
     * @return 底层 FileInfo
     */
    static FileInfo toFileInfo(String platform, String basePath, String path) {
        FileInfo info = new FileInfo();
        info.setPlatform(platform);
        info.setBasePath(basePath);
        info.setPath(directory(path));
        info.setFilename(filename(path));
        return info;
    }

    private OssPaths() {
    }
}
