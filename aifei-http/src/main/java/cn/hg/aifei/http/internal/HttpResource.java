/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.internal;

import cn.hg.aifei.http.HttpException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

/**
 * 资源名拼装：出站埋点维度 {@code METHOD host path} 的唯一定义点。
 *
 * <p>显式资源名完全替代推导；未显式指定时按 URL 推导——方法大写、去掉 query 与 fragment、authority 含非默认
 * 端口、空 path 归一为 {@code /}。</p>
 */
public final class HttpResource {

    /**
     * 默认端口占位：{@code URL#getPort()} 未显式给出端口时返回该值。
     */
    private static final int NO_PORT = -1;

    /**
     * 空 path 的归一值。
     */
    private static final String ROOT_PATH = "/";

    /**
     * 禁止创建工具类实例。
     */
    private HttpResource() {
    }

    /**
     * 拼装资源名。
     *
     * @param explicitResource 显式资源名，为 {@code null} 时按 {@code method} 与 {@code rawUrl} 推导
     * @param method           请求方法
     * @param rawUrl           完整请求 URL（可含 query）
     * @return 资源名，形如 {@code GET api.example.com/v1/orders}
     * @throws HttpException {@code rawUrl} 非法且未显式指定资源名（消息含原值）
     */
    public static String of(String explicitResource, String method, String rawUrl) {
        if (explicitResource != null) {
            return explicitResource.trim();
        }
        URL url;
        try {
            url = new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new HttpException("URL 非法: " + rawUrl, e);
        }
        int port = url.getPort();
        String authority = port == NO_PORT ? url.getHost() : url.getHost() + ":" + port;
        String path = url.getPath();
        if (path.isEmpty()) {
            path = ROOT_PATH;
        }
        return method.toUpperCase(Locale.ROOT) + " " + authority + path;
    }
}
