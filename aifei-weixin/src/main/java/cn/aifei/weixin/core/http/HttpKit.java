/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.http;

import java.io.File;
import java.util.Map;

/**
 * HTTP 客户端抽象（接口化便于单测注入伪实现，全程不联网）。
 */
public interface HttpKit {

    String get(String url);

    String post(String url, String jsonBody);

    String postMultipart(String url, Map<String, String> fields, Map<String, File> files);

    byte[] download(String url);

    /**
     * POST JSON 请求并下载二进制响应（小程序码 getwxacodeunlimit 等接口）。
     * 默认不支持，需要该能力的实现自行覆写。
     */
    default byte[] postDownload(String url, String jsonBody) {
        throw new UnsupportedOperationException("该 HttpKit 实现不支持 postDownload");
    }
}
