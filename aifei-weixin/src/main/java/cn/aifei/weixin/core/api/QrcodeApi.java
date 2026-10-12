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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 账号二维码与短链接。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Account_Management/Generating_a_Parametric_QR_Code.html
 */
public class QrcodeApi {

    public static ApiResult create(WeixinAccount account, Object payload) {
        return ApiCaller.post(account, "cgi-bin/qrcode/create", JSON.toJSONString(payload));
    }

    /**
     * 通过 ticket 换取二维码图片 URL（无需 access_token，可直接用于前端 img src）
     */
    public static String showQrcodeUrl(String ticket) {
        return "https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=" + ApiCaller.urlEncode(ticket);
    }

    public static ApiResult shortUrl(WeixinAccount account, String longUrl) {
        JSONObject body = new JSONObject();
        body.put("action", "long2short");
        body.put("long_url", longUrl);
        return ApiCaller.post(account, "cgi-bin/shorturl", body.toJSONString());
    }

    private QrcodeApi() {
    }
}
