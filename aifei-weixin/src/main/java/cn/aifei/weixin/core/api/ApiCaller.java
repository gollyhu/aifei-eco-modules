/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.api;

import cn.aifei.weixin.WxApiConfig;
import cn.aifei.weixin.common.ApiResult;
import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.core.cache.AccessTokenCache;
import cn.aifei.weixin.spi.WeixinAccount;
import com.alibaba.fastjson2.JSONObject;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Api 内部执行器（非对外 Api）：统一注入 access_token、按 appId 单飞取 token、
 * token 失效（40001 / 42001）时清缓存重取并重试一次。
 *
 * <p>提供两套等价入参重载：{@link WeixinAccount} 版本委托给 appId/secret 版本；
 * 小程序 {@code MiniAppAccount} 等无 token/aesKey 的账号直接走 appId/secret 版本。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Basic_Information/Get_access_token.html
 */
public class ApiCaller {

    public static final String API_BASE = "https://api.weixin.qq.com/";

    static final String TOKEN_PATH = "cgi-bin/token";

    /** token 提前 5 分钟过期，避免边界失效 */
    public static final long TOKEN_EXPIRE_AHEAD_MILLIS = 5 * 60 * 1000L;

    /** 按 appId 分段加锁实现单飞：并发未命中只发一次 token 请求 */
    private static final Map<String, Object> APP_LOCKS = new ConcurrentHashMap<>();

    /**
     * 取 access_token（appId/secret 直参版本，公众号与小程序通用），缓存未命中按 appId 单飞
     */
    public static String accessToken(String appId, String secret) {
        AccessTokenCache cache = WxApiConfig.tokenCache();
        String cached = cache.get(appId);
        if (cached != null) {
            return cached;
        }

        Object lock = APP_LOCKS.computeIfAbsent(appId, key -> new Object());
        synchronized (lock) {
            cached = cache.get(appId);
            if (cached != null) {
                return cached;
            }

            String url = API_BASE + TOKEN_PATH
                    + "?grant_type=client_credential"
                    + "&appid=" + appId
                    + "&secret=" + secret;
            ApiResult result = ApiResult.parse(WxApiConfig.http().get(url));
            if (!result.isSucceed()) {
                throw new WxApiException("获取 access_token 失败：" + result.getErrorCode() + " " + result.getErrorMsg());
            }

            JSONObject data = result.getData();
            String token = data.getString("access_token");
            if (token == null || token.isEmpty()) {
                throw new WxApiException("获取 access_token 失败：响应缺少 access_token");
            }
            int expiresIn = data.getIntValue("expires_in", 7200);
            cache.set(appId, token, System.currentTimeMillis() + expiresIn * 1000L - TOKEN_EXPIRE_AHEAD_MILLIS);
            return token;
        }
    }

    public static String accessToken(WeixinAccount account) {
        return accessToken(account.getAppId(), account.getSecret());
    }

    public static ApiResult get(String appId, String secret, String path, Map<String, Object> params) {
        return execute(appId, secret, path, params, null, true);
    }

    public static ApiResult get(WeixinAccount account, String path, Map<String, Object> params) {
        return get(account.getAppId(), account.getSecret(), path, params);
    }

    public static ApiResult post(String appId, String secret, String path, String jsonBody) {
        return execute(appId, secret, path, null, jsonBody, true);
    }

    public static ApiResult post(WeixinAccount account, String path, String jsonBody) {
        return post(account.getAppId(), account.getSecret(), path, jsonBody);
    }

    public static ApiResult postMultipart(String appId, String secret, String path,
                                          Map<String, String> fields, Map<String, File> files) {
        String url = buildUrl(path, accessToken(appId, secret), null);
        ApiResult result = ApiResult.parse(WxApiConfig.http().postMultipart(url, fields, files));
        if (!isTokenInvalid(result)) {
            return result;
        }

        WxApiConfig.tokenCache().remove(appId);
        String retryUrl = buildUrl(path, accessToken(appId, secret), null);
        return ApiResult.parse(WxApiConfig.http().postMultipart(retryUrl, fields, files));
    }

    public static ApiResult postMultipart(WeixinAccount account, String path,
                                          Map<String, String> fields, Map<String, File> files) {
        return postMultipart(account.getAppId(), account.getSecret(), path, fields, files);
    }

    public static byte[] download(String appId, String secret, String path, Map<String, Object> params) {
        byte[] bytes = WxApiConfig.http().download(buildUrl(path, accessToken(appId, secret), params));
        if (isTokenInvalidJson(bytes)) {
            WxApiConfig.tokenCache().remove(appId);
            return WxApiConfig.http().download(buildUrl(path, accessToken(appId, secret), params));
        }
        return bytes;
    }

    public static byte[] download(WeixinAccount account, String path, Map<String, Object> params) {
        return download(account.getAppId(), account.getSecret(), path, params);
    }

    /**
     * POST JSON 并下载二进制响应（小程序码等接口），token 失效时清缓存重试一次
     */
    public static byte[] postDownload(String appId, String secret, String path, String jsonBody) {
        byte[] bytes = WxApiConfig.http().postDownload(buildUrl(path, accessToken(appId, secret), null), jsonBody);
        if (isTokenInvalidJson(bytes)) {
            WxApiConfig.tokenCache().remove(appId);
            return WxApiConfig.http().postDownload(buildUrl(path, accessToken(appId, secret), null), jsonBody);
        }
        return bytes;
    }

    public static byte[] postDownload(WeixinAccount account, String path, String jsonBody) {
        return postDownload(account.getAppId(), account.getSecret(), path, jsonBody);
    }

    /**
     * 下载失败时微信以 JSON errcode 报错（HTTP 200），正常素材为二进制；仅在可解析为
     * token 失效错误时触发重试，二进制内容原样返回
     */
    public static boolean isTokenInvalidJson(byte[] bytes) {
        try {
            return isTokenInvalid(ApiResult.parse(new String(bytes, StandardCharsets.UTF_8)));
        } catch (WxApiException e) {
            return false;
        }
    }

    /**
     * 不注入 access_token 的请求（网页授权 sns/* 接口自带 appid/secret）
     */
    public static ApiResult requestWithoutToken(String path, Map<String, Object> params) {
        return ApiResult.parse(WxApiConfig.http().get(buildUrl(path, null, params)));
    }

    /**
     * URL 编码（UTF-8），供各 Api 拼接 query 复用
     */
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JDK 不支持 UTF-8", e);
        }
    }

    public static boolean isTokenInvalid(ApiResult result) {
        int errorCode = result.getErrorCode();
        return errorCode == 40001 || errorCode == 42001;
    }

    private static ApiResult execute(String appId, String secret, String path, Map<String, Object> params,
                                     String jsonBody, boolean allowRetry) {
        String url = buildUrl(path, accessToken(appId, secret), params);
        String json = jsonBody == null ? WxApiConfig.http().get(url) : WxApiConfig.http().post(url, jsonBody);
        ApiResult result = ApiResult.parse(json);

        if (allowRetry && isTokenInvalid(result)) {
            WxApiConfig.tokenCache().remove(appId);
            return execute(appId, secret, path, params, jsonBody, false);
        }
        return result;
    }

    /**
     * 拼接 URL：{@code API_BASE + path + ?access_token=xxx&k=v}
     */
    static String buildUrl(String path, String accessToken, Map<String, Object> params) {
        StringBuilder url = new StringBuilder(API_BASE).append(path);
        char separator = '?';
        if (accessToken != null) {
            url.append(separator).append("access_token=").append(accessToken);
            separator = '&';
        }
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                url.append(separator).append(urlEncode(entry.getKey())).append('=').append(urlEncode(String.valueOf(entry.getValue())));
                separator = '&';
            }
        }
        return url.toString();
    }

    private ApiCaller() {
    }
}
