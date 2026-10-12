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
 * 用户管理。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/User_Management/Get_users_basic_information_UnionID.html
 */
public class UserApi {

    public static ApiResult getUserInfo(WeixinAccount account, String openId, String lang) {
        Map<String, Object> params = new HashMap<>();
        params.put("openid", openId);
        params.put("lang", lang == null ? "zh_CN" : lang);
        return ApiCaller.get(account, "cgi-bin/user/info", params);
    }

    public static ApiResult getFansList(WeixinAccount account, String nextOpenId) {
        Map<String, Object> params = new HashMap<>();
        params.put("next_openid", nextOpenId);
        return ApiCaller.get(account, "cgi-bin/user/get", params);
    }

    private UserApi() {
    }
}
