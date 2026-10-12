/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.http;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 单测用伪 HTTP 客户端：记录请求、按需返回、可模拟传输异常，全程不联网
 */
public class FakeHttpKit implements HttpKit {

    public final List<String> getUrls = new ArrayList<>();
    public final List<String> postUrls = new ArrayList<>();
    public final List<String> postBodies = new ArrayList<>();
    public final List<String> multipartUrls = new ArrayList<>();
    public final List<String> downloadUrls = new ArrayList<>();

    public final Map<String, String> lastFields = new HashMap<>();
    public final Map<String, File> lastFiles = new HashMap<>();

    public String tokenResponse = "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}";
    public Supplier<String> getSupplier = () -> "{\"errcode\":0,\"errmsg\":\"ok\"}";
    public Supplier<String> postSupplier = () -> "{\"errcode\":0,\"errmsg\":\"ok\"}";
    public Supplier<String> multipartSupplier = () -> "{\"errcode\":0,\"errmsg\":\"ok\"}";
    public byte[] downloadResponse = new byte[0];
    public Supplier<byte[]> downloadSupplier;
    public Supplier<byte[]> postDownloadSupplier = () -> new byte[0];
    public final List<String> postDownloadUrls = new ArrayList<>();
    public final List<String> postDownloadBodies = new ArrayList<>();
    public int tokenRequestCount;

    @Override
    public String get(String url) {
        getUrls.add(url);
        if (url.contains("/cgi-bin/token")) {
            tokenRequestCount++;
            return tokenResponse;
        }
        return getSupplier.get();
    }

    @Override
    public String post(String url, String jsonBody) {
        postUrls.add(url);
        postBodies.add(jsonBody);
        return postSupplier.get();
    }

    @Override
    public String postMultipart(String url, Map<String, String> fields, Map<String, File> files) {
        multipartUrls.add(url);
        lastFields.clear();
        lastFiles.clear();
        if (fields != null) {
            lastFields.putAll(fields);
        }
        if (files != null) {
            lastFiles.putAll(files);
        }
        return multipartSupplier.get();
    }

    @Override
    public byte[] download(String url) {
        downloadUrls.add(url);
        return downloadSupplier != null ? downloadSupplier.get() : downloadResponse;
    }

    @Override
    public byte[] postDownload(String url, String jsonBody) {
        postDownloadUrls.add(url);
        postDownloadBodies.add(jsonBody);
        return postDownloadSupplier.get();
    }
}
