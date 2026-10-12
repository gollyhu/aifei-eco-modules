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
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 素材管理（临时素材 + 永久素材）。
 *
 * <p>官方文档：https://developers.weixin.qq.com/doc/offiaccount/Asset_Management/New_temporary_materials.html
 * <br>大小限制（图片/语音 2MB、视频 10MB）由调用方自行控制。
 */
public class MediaApi {

    public static ApiResult uploadTemp(WeixinAccount account, String type, File file) {
        Map<String, String> fields = new HashMap<>();
        fields.put("type", type);
        Map<String, File> files = new HashMap<>();
        files.put("media", file);
        return ApiCaller.postMultipart(account, "cgi-bin/media/upload", fields, files);
    }

    /**
     * 获取临时素材二进制内容（3 天有效）
     */
    public static byte[] getTemp(WeixinAccount account, String mediaId) {
        Map<String, Object> params = new HashMap<>();
        params.put("media_id", mediaId);
        return ApiCaller.download(account, "cgi-bin/media/get", params);
    }

    public static ApiResult addMaterial(WeixinAccount account, String type, File file, String title, String introduction) {
        Map<String, String> fields = new HashMap<>();
        fields.put("type", type);
        JSONObject description = new JSONObject();
        description.put("title", title);
        description.put("introduction", introduction);
        fields.put("description", description.toJSONString());

        Map<String, File> files = new HashMap<>();
        files.put("media", file);
        return ApiCaller.postMultipart(account, "cgi-bin/material/add_material", fields, files);
    }

    /**
     * 新增永久图文素材
     *
     * @param articles 图文素材数组（JSON 结构见官方文档）
     */
    public static ApiResult addNewsMaterial(WeixinAccount account, Object articles) {
        JSONObject body = new JSONObject();
        body.put("articles", articles);
        return ApiCaller.post(account, "cgi-bin/material/add_news", JSON.toJSONString(body));
    }

    private MediaApi() {
    }
}
