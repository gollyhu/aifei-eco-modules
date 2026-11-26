/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.pay.internal.channels;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ijpay.core.IJPayHttpResponse;
import com.ijpay.core.kit.WxPayKit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 渠道响应归一：XML / JSON 报文统一转成扁平 Map，交给 {@code internal.PayResults} 转换。
 */
final class ChannelResponses {

    private ChannelResponses() {
    }

    static Map<String, String> xml(String body) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        return WxPayKit.xmlToMap(body);
    }

    static Map<String, String> json(String body) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (body == null || body.trim().isEmpty()) {
            return result;
        }
        JSONObject object = JSONUtil.parseObj(body);
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                result.put(entry.getKey(), String.valueOf(value));
            }
        }
        return result;
    }

    static Map<String, String> body(IJPayHttpResponse response) {
        return response == null ? Collections.<String, String>emptyMap() : json(response.getBody());
    }

    static String text(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value;
    }
}
