/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin;

import cn.aifei.weixin.common.WxApiException;
import cn.aifei.weixin.core.cache.AccessTokenCache;
import cn.aifei.weixin.core.http.HttpKit;

/**
 * Api 链路配置入口：静态 *Api 由此取得 {@link HttpKit} 与 {@link AccessTokenCache}。
 *
 * <pre>
 * 使用约定：
 *  1: 应用启动时装配一次 WxApiConfig.set(http, tokenCache)
 *  2: tokenCache 必须复用同一实例，否则缓存失效并触发微信取 token 配额问题
 *  3: 允许重复调用覆盖，以最后一次为准（仅供测试场景隔离使用）
 *  4: 未装配即调用 → 抛 WxApiException，避免隐式 NPE
 * </pre>
 */
public class WxApiConfig {

    private static volatile HttpKit http;
    private static volatile AccessTokenCache tokenCache;

    public static void set(HttpKit httpKit, AccessTokenCache accessTokenCache) {
        http = httpKit;
        tokenCache = accessTokenCache;
    }

    public static HttpKit http() {
        HttpKit ret = http;
        if (ret == null) {
            throw new WxApiException("WxApiConfig 未初始化，请先在应用启动时调用 WxApiConfig.set(http, tokenCache)");
        }
        return ret;
    }

    public static AccessTokenCache tokenCache() {
        AccessTokenCache ret = tokenCache;
        if (ret == null) {
            throw new WxApiException("WxApiConfig 未初始化，请先在应用启动时调用 WxApiConfig.set(http, tokenCache)");
        }
        return ret;
    }

    private WxApiConfig() {
    }
}
