/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.core.api;

import cn.aifei.weixin.common.ApiResult;
import cn.aifei.weixin.spi.WeixinAccount;
import java.util.HashMap;
import java.util.Map;

/**
 * 网页授权（sns/* 接口自带 appid/secret，不走 access_token）。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/Wechat_webpage_authorization.html
 */
public class OAuth2Api {

    /**
     * 拼装授权跳转 URL。scope 取值：snsapi_base（静默）/ snsapi_userinfo（需用户确认）
     */
    public static String buildAuthorizeUrl(WeixinAccount account, String redirectUri, String scope, String state) {
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
                + "?appid=" + account.getAppId()
                + "&redirect_uri=" + ApiCaller.urlEncode(redirectUri)
                + "&response_type=code"
                + "&scope=" + scope
                + "&state=" + (state == null ? "" : state)
                + "#wechat_redirect";
    }

    public static ApiResult getAccessToken(WeixinAccount account, String code) {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", account.getAppId());
        params.put("secret", account.getSecret());
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        return ApiCaller.requestWithoutToken("sns/oauth2/access_token", params);
    }

    public static ApiResult getUserInfo(WeixinAccount account, String accessToken, String openId, String lang) {
        Map<String, Object> params = new HashMap<>();
        params.put("access_token", accessToken);
        params.put("openid", openId);
        params.put("lang", lang == null ? "zh_CN" : lang);
        return ApiCaller.requestWithoutToken("sns/userinfo", params);
    }

    public static ApiResult refreshToken(WeixinAccount account, String refreshToken) {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", account.getAppId());
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        return ApiCaller.requestWithoutToken("sns/oauth2/refresh_token", params);
    }

    private OAuth2Api() {
    }
}
