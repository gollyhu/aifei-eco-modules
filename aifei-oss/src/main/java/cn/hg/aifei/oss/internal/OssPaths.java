/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.internal;

import cn.hg.aifei.oss.OssException;
import org.dromara.x.file.storage.core.FileInfo;

/**
 * 对象路径与 {@link FileInfo} 之间换算的<b>唯一定义点</b>，同时是对象路径边界校验的唯一入口。
 *
 * <p><b>路径语义</b>：{@code path} 是平台 {@code base-path} 之下的完整逻辑对象 key，如
 * {@code upload/2026/a.png}。切分口径为：</p>
 *
 * <pre>
 * int i = path.lastIndexOf('/');
 * String dir      = (i &lt; 0) ? "" : path.substring(0, i + 1); // 保留尾 '/'，无 '/' 时为空串
 * String filename = path.substring(i + 1);
 * </pre>
 *
 * <p><b>为何保留尾 {@code '/'}</b>：底层按 {@code basePath + path + filename} <b>逐字拼接、不补分隔符</b>
 * 得到物理 key，故 {@code dir} 必须自带尾 {@code '/'}，否则会粘连成 {@code upload2026/a.png} 之类。上传时以
 * {@code setPath(dir).setSaveFilename(filename)} 写入，与下载侧 {@link #toFileInfo(String, String, String)}
 * 的换算互为逆运算，保证「按路径再次定位」成立。</p>
 *
 * <p><b>边界校验</b>：{@code path} 为空白、以 {@code /} 开头、含 {@code ..}、末段为空，均抛
 * {@link OssException}；其它实现类不得复制该校验。</p>
 *
 * <p>本类属非承诺 API：因装配需要声明为 {@code public}，但不是公共 API，消费方不得引用。</p>
 */
public final class OssPaths {

    /**
     * 禁止创建工具类实例。
     */
    private OssPaths() {
    }

    /**
     * 校验对象路径并返回其目录段（含尾 {@code '/'}，无 {@code '/'} 时为 {@code ""}）。
     *
     * @param path 对象路径
     * @return 目录段，形如 {@code upload/2026/}；路径不含 {@code '/'} 时为空串
     * @throws OssException 路径空白、以 {@code /} 开头、含 {@code ..} 或末段为空
     */
    public static String directoryOf(String path) {
        String valid = requirePath(path);
        int i = valid.lastIndexOf('/');
        return (i < 0) ? "" : valid.substring(0, i + 1);
    }

    /**
     * 校验对象路径并返回其末段文件名。
     *
     * @param path 对象路径
     * @return 末段文件名
     * @throws OssException 路径空白、以 {@code /} 开头、含 {@code ..} 或末段为空
     */
    public static String filenameOf(String path) {
        String valid = requirePath(path);
        return valid.substring(valid.lastIndexOf('/') + 1);
    }

    /**
     * 把目录段与文件名拼回完整逻辑对象路径（目录段自带尾 {@code '/'}）。
     *
     * @param dir 目录段
     * @param filename 文件名
     * @return 完整逻辑对象路径
     */
    public static String join(String dir, String filename) {
        String d = (dir == null) ? "" : dir;
        String f = (filename == null) ? "" : filename;
        return d + f;
    }

    /**
     * 按分隔口径把对象路径换算为可定位的 {@link FileInfo}。
     *
     * @param platform 所属存储平台标识
     * @param basePath 平台配置的 {@code base-path}，可为 {@code null}
     * @param path 对象路径
     * @return 含 {@code platform} / {@code basePath} / 目录段 {@code path} / 末段 {@code filename} 的
     *         文件信息
     * @throws OssException 路径非法
     */
    public static FileInfo toFileInfo(String platform, String basePath, String path) {
        String valid = requirePath(path);
        int i = valid.lastIndexOf('/');
        String dir = (i < 0) ? "" : valid.substring(0, i + 1);
        String filename = valid.substring(i + 1);
        return new FileInfo(basePath, dir, filename).setPlatform(platform);
    }

    /**
     * 对象路径的边界校验；实现类不得复制。
     *
     * @param path 对象路径
     * @return 原路径
     * @throws OssException 路径空白、以 {@code /} 开头、含 {@code ..} 或末段为空
     */
    private static String requirePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new OssException("oss object path can not be blank");
        }
        if (path.startsWith("/")) {
            throw new OssException("oss object path must not start with '/': " + path);
        }
        if (path.contains("..")) {
            throw new OssException("oss object path must not contain '..': " + path);
        }
        if (path.charAt(path.length() - 1) == '/') {
            throw new OssException("oss object path must not end with '/': " + path);
        }
        return path;
    }
}
