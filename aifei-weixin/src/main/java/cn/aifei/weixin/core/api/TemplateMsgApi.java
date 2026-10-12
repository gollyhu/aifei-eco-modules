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

/**
 * 模板消息。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Message_Management/Template_Message_Interface.html
 */
public class TemplateMsgApi {

    public static ApiResult send(WeixinAccount account, Object payload) {
        return ApiCaller.post(account, "cgi-bin/message/template/send", JSON.toJSONString(payload));
    }

    private TemplateMsgApi() {
    }
}
