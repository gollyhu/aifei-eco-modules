/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 *
 * 授权：aifei vip 订阅用户。尊重版权，民富国强！
 */

package cn.aifei.weixin.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 微信接口统一返回。
 *
 * <p>错误契约：HTTP 正常时一律返回 ApiResult；微信 errcode != 0 时不抛异常，
 * 由调用方通过 {@link #isSucceed()} / {@link #getErrorCode()} 判断。
 */
public class ApiResult {

    private final String json;
    private final JSONObject data;

    private ApiResult(String json, JSONObject data) {
        this.json = json;
        this.data = data;
    }

    /**
     * 解析响应体；响应体非 JSON 时抛 {@link WxApiException}（传输层失败）
     */
    public static ApiResult parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new WxApiException("微信接口响应体为空");
        }

        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (RuntimeException e) {
            throw new WxApiException("微信接口响应体非 JSON: " + abbreviate(json), e);
        }
        if (obj == null) {
            throw new WxApiException("微信接口响应体非 JSON 对象: " + abbreviate(json));
        }
        return new ApiResult(json, obj);
    }

    /**
     * errcode == 0 或无 errcode 字段即为成功
     */
    public boolean isSucceed() {
        return !data.containsKey("errcode") || data.getIntValue("errcode", 0) == 0;
    }

    public int getErrorCode() {
        return data.getIntValue("errcode", 0);
    }

    public String getErrorMsg() {
        return data.getString("errmsg");
    }

    public String getJson() {
        return json;
    }

    public JSONObject getData() {
        return data;
    }

    private static String abbreviate(String str) {
        return str.length() > 200 ? str.substring(0, 200) + "..." : str;
    }

    @Override
    public String toString() {
        return json;
    }
}
