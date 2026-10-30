/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.oss.demo.service;

import cn.aifei.aop.Inject;
import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.hg.aifei.oss.OssClient;
import cn.hg.aifei.oss.OssFileInfo;
import cn.hg.aifei.oss.OssKit;
import cn.hg.aifei.oss.OssPage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 Service：现场演示 aifei-oss 的上传 / if-absent / 下载 / 列举 / 复制 / 删除。
 *
 * <p>所有端点均为 GET，返回 {@link Out}（HTTP 恒 200，业务状态由 {@code code} 承载）。
 * {@code OssClient} 由装配入口注册，此处按接口类型 {@code @Inject} 取用，即本模块的标准用法。</p>
 */
@Path("/oss/demo")
public class OssDemoService {

    /**
     * 装配入口注册的默认平台客户端。
     */
    @Inject
    OssClient client;

    /**
     * 返回已注册平台列表，是现场验证的主入口。
     *
     * @return 平台快照
     */
    @Path("state")
    public Out state() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("platforms", new ArrayList<String>(OssKit.platforms()));
        return Out.ok().data(data);
    }

    /**
     * 上传一段文本到指定路径。
     *
     * @param path 对象路径
     * @param text 文本内容
     * @return 上传结果与元数据
     */
    @Path("upload")
    public Out upload(@Para(name = "path", defaultValue = "demo/hello.txt") String path,
                      @Para(name = "text", defaultValue = "hello aifei-oss") String text) {
        try {
            OssFileInfo info = client.upload(text.getBytes(StandardCharsets.UTF_8), path, "text/plain");
            return Out.ok("上传成功").data(describe(info));
        } catch (RuntimeException e) {
            return Out.fail("上传失败: " + e.getMessage());
        }
    }

    /**
     * 仅当对象不存在时上传（已存在则不覆盖）。
     *
     * @param path 对象路径
     * @param text 文本内容
     * @return 上传结果与元数据
     */
    @Path("upload-if-absent")
    public Out uploadIfAbsent(@Para(name = "path", defaultValue = "demo/hello.txt") String path,
                              @Para(name = "text", defaultValue = "hello aifei-oss") String text) {
        try {
            OssFileInfo info = client.uploadIfAbsent(text.getBytes(StandardCharsets.UTF_8), path, "text/plain");
            return Out.ok("已存在则不覆盖").data(describe(info));
        } catch (RuntimeException e) {
            return Out.fail("if-absent 上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载对象并返回文本内容。
     *
     * @param path 对象路径
     * @return 文本内容
     */
    @Path("download")
    public Out download(@Para(name = "path", defaultValue = "demo/hello.txt") String path) {
        try {
            byte[] bytes = client.download(path).bytes();
            return Out.ok().data(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return Out.fail("下载失败: " + e.getMessage());
        }
    }

    /**
     * 判断对象是否存在。
     *
     * @param path 对象路径
     * @return 存在性
     */
    @Path("exists")
    public Out exists(@Para(name = "path", defaultValue = "demo/hello.txt") String path) {
        try {
            return Out.ok().data(client.exists(path));
        } catch (RuntimeException e) {
            return Out.fail("判断失败: " + e.getMessage());
        }
    }

    /**
     * 列举目录下的对象（一次性）。
     *
     * @param path 目录路径
     * @return 对象列表
     */
    @Path("list")
    public Out list(@Para(name = "path", defaultValue = "demo") String path) {
        try {
            List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
            for (OssFileInfo file : client.listFiles(path)) {
                data.add(describe(file));
            }
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("列举失败: " + e.getMessage());
        }
    }

    /**
     * 分页列举目录下的对象与子目录。
     *
     * @param path     目录路径
     * @param maxFiles 本页最多对象数
     * @param marker   上一页游标
     * @return 分页结果
     */
    @Path("page")
    public Out page(@Para(name = "path", defaultValue = "demo") String path,
                    @Para(name = "maxFiles", defaultValue = "2") int maxFiles,
                    @Para(name = "marker", defaultValue = "") String marker) {
        try {
            OssPage page = client.listFiles(path, maxFiles, marker.isEmpty() ? null : marker);
            List<Map<String, Object>> files = new ArrayList<Map<String, Object>>();
            for (OssFileInfo file : page.getFiles()) {
                files.add(describe(file));
            }
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("files", files);
            data.put("directories", page.getDirectories());
            data.put("nextMarker", page.getNextMarker());
            data.put("truncated", page.isTruncated());
            return Out.ok().data(data);
        } catch (RuntimeException e) {
            return Out.fail("分页列举失败: " + e.getMessage());
        }
    }

    /**
     * 同平台内复制（服务端复制优先，不支持时降级下载+上传）。
     *
     * @param src  源路径
     * @param dest 目标路径
     * @return 复制结果
     */
    @Path("copy")
    public Out copy(@Para(name = "src", defaultValue = "demo/hello.txt") String src,
                    @Para(name = "dest", defaultValue = "demo/hello-copy.txt") String dest) {
        try {
            return Out.ok("复制成功").data(client.copy(src, dest));
        } catch (RuntimeException e) {
            return Out.fail("复制失败: " + e.getMessage());
        }
    }

    /**
     * 删除对象。
     *
     * @param path 对象路径
     * @return 删除结果
     */
    @Path("delete")
    public Out delete(@Para(name = "path", defaultValue = "demo/hello.txt") String path) {
        try {
            return Out.ok().data(client.delete(path));
        } catch (RuntimeException e) {
            return Out.fail("删除失败: " + e.getMessage());
        }
    }

    /**
     * 生成限时访问地址（平台不支持时返回失败）。
     *
     * @param path       对象路径
     * @param ttlSeconds 有效期（秒）
     * @return 限时访问地址
     */
    @Path("url")
    public Out url(@Para(name = "path", defaultValue = "demo/hello.txt") String path,
                   @Para(name = "ttl", defaultValue = "3600") long ttlSeconds) {
        try {
            return Out.ok().data(client.generatePresignedUrl(path, ttlSeconds));
        } catch (RuntimeException e) {
            return Out.fail("生成签名 URL 失败: " + e.getMessage());
        }
    }

    private static Map<String, Object> describe(OssFileInfo info) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("path", info.getPath());
        data.put("size", info.getSize());
        data.put("url", info.getUrl());
        return data;
    }
}
