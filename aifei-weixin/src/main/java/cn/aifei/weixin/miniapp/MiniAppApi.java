/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.miniapp;

import cn.aifei.weixin.common.ApiResult;
import cn.aifei.weixin.core.api.ApiCaller;
import com.alibaba.fastjson2.JSON;
import java.util.HashMap;
import java.util.Map;

/**
 * 小程序服务端接口（复用公众号 ApiCaller 的 token 管理与 HttpKit）。
 */
public class MiniAppApi {

    /**
     * 登录凭证校验（wx.login 的 code 换 openid / unionid / session_key）。
     *
     * <p>官方文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
     *
     * @throws cn.aifei.weixin.common.WxApiException 业务失败（如 40029 code 无效）或响应缺字段
     */
    public static Code2Session code2Session(MiniAppAccount account, String code) {
        Map<String, Object> params = new HashMap<>();
        params.put("appid", account.getAppId());
        params.put("secret", account.getSecret());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");
        return Code2Session.parse(ApiCaller.requestWithoutToken("sns/jscode2session", params));
    }

    /**
     * 订阅消息发送。
     *
     * <p>官方文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/mp-message-management/subscribe-message/sendMessage.html
     */
    public static ApiResult subscribeSend(MiniAppAccount account, Object payload) {
        return ApiCaller.post(account.getAppId(), account.getSecret(),
                "cgi-bin/message/subscribe/send", JSON.toJSONString(payload));
    }

    /**
     * 获取手机号（button 开放能力 phonenumber 的 code 换手机号）。
     *
     * <p>官方文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html
     */
    public static ApiResult getUserPhoneNumber(MiniAppAccount account, String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        return ApiCaller.post(account.getAppId(), account.getSecret(),
                "wxa/business/getuserphonenumber", JSON.toJSONString(body));
    }

    /**
     * 获取不限制数量的小程序码，返回图片二进制（失败时微信以 JSON errcode 返回，原样交由调用方判别）。
     *
     * <p>官方文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/wx-app-code/getWxaCodeUnlimit.html
     */
    public static byte[] getWxacodeUnlimit(MiniAppAccount account, Object payload) {
        return ApiCaller.postDownload(account.getAppId(), account.getSecret(),
                "wxa/getwxacodeunlimit", JSON.toJSONString(payload));
    }

    private MiniAppApi() {
    }
}
