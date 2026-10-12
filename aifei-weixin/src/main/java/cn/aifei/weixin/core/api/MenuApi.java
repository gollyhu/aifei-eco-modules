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
 * 自定义菜单。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Custom_Menus/Creating_Custom-Defined_Menu.html
 */
public class MenuApi {

    public static ApiResult create(WeixinAccount account, Object menu) {
        return ApiCaller.post(account, "cgi-bin/menu/create", JSON.toJSONString(menu));
    }

    public static ApiResult get(WeixinAccount account) {
        return ApiCaller.get(account, "cgi-bin/menu/get", null);
    }

    public static ApiResult delete(WeixinAccount account) {
        return ApiCaller.get(account, "cgi-bin/menu/delete", null);
    }

    private MenuApi() {
    }
}
