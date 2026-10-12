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
import cn.aifei.weixin.core.cache.AccessTokenCache;
import cn.aifei.weixin.core.encrypt.Sha1Kit;
import cn.aifei.weixin.spi.WeixinAccount;
import com.alibaba.fastjson2.JSONObject;
import java.util.HashMap;
import java.util.Map;

/**
 * JS-SDK 票据与前端签名。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/OA_Web_Apps/JS-SDK.html
 *
 * <p>jsapi_ticket 复用 {@link AccessTokenCache} 同一实例缓存，key 前缀 {@code jsapi_ticket:{appId}}，
 * 不新增第二套缓存接口。
 */
public class JsTicketApi {

    static final String TICKET_PATH = "cgi-bin/ticket/getticket";

    static final String TICKET_CACHE_PREFIX = "jsapi_ticket:";

    public static ApiResult getTicket(WeixinAccount account) {
        AccessTokenCache cache = WxApiConfig.tokenCache();
        String cacheKey = TICKET_CACHE_PREFIX + account.getAppId();
        String cached = cache.get(cacheKey);
        if (cached != null) {
            JSONObject data = new JSONObject();
            data.put("errcode", 0);
            data.put("errmsg", "ok");
            data.put("ticket", cached);
            data.put("expires_in", 7200);
            return ApiResult.parse(data.toJSONString());
        }

        Map<String, Object> params = new HashMap<>();
        params.put("type", "jsapi");
        ApiResult result = ApiCaller.get(account, TICKET_PATH, params);
        if (result.isSucceed()) {
            JSONObject data = result.getData();
            String ticket = data.getString("ticket");
            if (ticket != null && !ticket.isEmpty()) {
                int expiresIn = data.getIntValue("expires_in", 7200);
                cache.set(cacheKey, ticket, System.currentTimeMillis()
                        + expiresIn * 1000L - ApiCaller.TOKEN_EXPIRE_AHEAD_MILLIS);
            }
        }
        return result;
    }

    /**
     * 前端 JS-SDK 签名：sha1("jsapi_ticket=..&noncestr=..&timestamp=..&url=..")，字符串顺序固定不排序
     */
    public static String sign(String jsapiTicket, String nonce, long timestamp, String url) {
        return Sha1Kit.sha1("jsapi_ticket=" + jsapiTicket
                + "&noncestr=" + nonce
                + "&timestamp=" + timestamp
                + "&url=" + url);
    }

    private JsTicketApi() {
    }
}
