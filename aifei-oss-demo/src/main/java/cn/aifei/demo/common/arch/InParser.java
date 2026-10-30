/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import com.alibaba.fastjson2.*;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.*;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.io.IOException;
import java.util.*;

/**
 * InParser
 */
public class InParser {

    static final FormParserFactory formParserFactory = FormParserFactory
            .builder(false)
            .withDefaultCharset("UTF-8")
            .addParser(new FormEncodedDataDefinition())
            .build();

    /**
     * 解析数据，注意返回值必须为 "非 null"，避免重复解析。
     */
    public JSONObject parse(HttpServerExchange exchange, In in) {
        JSONObject queryPara = parseQueryPara(exchange);
        JSONObject bodyPara = parseBodyPara(exchange, in);

        // 尽可能避免创建对象：两类解析结果允许为 null，但必须确保返回非 null
        if (bodyPara != null) {
            if (queryPara != null) {
                bodyPara.putAll(queryPara); // queryPara 覆盖同名数据
            }
            return bodyPara;

        } else {
            return queryPara != null ? queryPara : new JSONObject();
        }
    }

    // query para 不支持同名变量
    JSONObject parseQueryPara(HttpServerExchange exchange) {
        Map<String, Deque<String>> queryParas = exchange.getQueryParameters();
        if (queryParas.isEmpty()) {
            return null;
        }

        JSONObject ret = new JSONObject(queryParas.size());
        for (Map.Entry<String, Deque<String>> e : queryParas.entrySet()) {
            // 当前版本 undertow 的 e.getValue() 返回值不可能为 null
            String first = e.getValue().getFirst();
            ret.put(e.getKey(), getStringValue(first));
        }
        return ret;
    }

    /*
     * 表单域格式数据在字段存在但没有输入值时，提交后的值为空字符串 ""，需要将 "" 转为 null。
     */
    String getStringValue(String str) {
        // 长度为 0 的字符串 "" 处理成 null，注意空格不会处理成 null
        return str == null || str.isEmpty() ? null : str;
    }

    /**
     * 解析 http 请求体数据，包括 json 数据和 form 表单数据
     *
     * <pre>
     * Json 内容认定：
     *  1: json 常见 content type 值：application/json、application/ld+json、application/vnd.api+json。
     *  2: 为了极致性能未考虑以下两类极端情况，需根据实际应用情况调整本方法逻辑：
     *      json 并非小写，例如：Application/JSON
     *      header 参数误命中，例如：text/plain; profile="json"
     * </pre>
     */
    JSONObject parseBodyPara(HttpServerExchange exchange, In in) {
        // HTTP 规范：GET 请求忽略 body
        if (Methods.GET.equals(exchange.getRequestMethod())) {
            return null;
        }

        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType != null && contentType.contains("json")) {
            return parseJsonPara(exchange, in);
        } else {
            return parseFormPara(exchange, in);
        }
    }

    // 解析 json 数据
    JSONObject parseJsonPara(HttpServerExchange exchange, In in) {
        // body 长度确定为 0 时跳过解析
        if (exchange.getRequestContentLength() == 0) {
            return null;
        }

        // Object json = JSON.parse(readBody(exchange));
        Object json = JSON.parse(exchange.getInputStream(), JSONFactory.createReadContext());

        if (json instanceof JSONObject) {
            return (JSONObject) json;
        } else {
            if (json instanceof JSONArray) {
                in.arrayData = (JSONArray) json;    // 保存 json 数组
            }
            return null;
        }
    }

    // 解析 form 表单数据
    JSONObject parseFormPara(HttpServerExchange exchange, In in) {
        FormData formData;
        try {
            // 坑：不能使用 try-with-resources 关闭 parser，否则上传文件会被删除，且 InputStream 被关闭
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                return null;
            } else {
                formData = parser.parseBlocking();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JSONObject ret = new JSONObject();
        for (String key : formData) {
            Deque<FormData.FormValue> deque = formData.get(key);
            if (deque != null) {
                int size = deque.size();
                if (size == 1) {
                    handleObject(key, deque, ret, in);
                } else if (size > 1) {
                    handleArray(key, deque, ret, in);
                }
            }
        }

        return ret;
    }

    void handleObject(String key, Deque<FormData.FormValue> deque, JSONObject ret, In in) {
        FormData.FormValue first = deque.getFirst();

        // undertow 下不会出现 null 值，在此仅为逻辑完备及防御式编程
        if (first == null) {
            int index = key.indexOf('.');
            if (index == -1) {
                ret.put(key, null);
            } else {
                String prefix = key.substring(0, index);
                String field = key.substring(index + 1);
                JSONObject jo = getOrCreateJSONObject(prefix, ret);
                jo.put(field, null);
            }
            return;
        }

        // 支持 account.id、account.username 这类格式数据
        int index = key.indexOf('.');
        if (index == -1) {
            ret.put(key, getStringValue(first.getValue()));

        } else {
            String prefix = key.substring(0, index);
            String field = key.substring(index + 1);
            JSONObject jo = getOrCreateJSONObject(prefix, ret);
            jo.put(field, getStringValue(first.getValue()));
        }
    }

    JSONObject getOrCreateJSONObject(String key, JSONObject ret) {
        JSONObject jo = ret.getJSONObject(key);
        if (jo == null) {
            jo = new JSONObject();
            ret.put(key, jo);
        }
        return jo;
    }

    void handleArray(String key, Deque<FormData.FormValue> deque, JSONObject ret, In in) {
        JSONArray array = new JSONArray(deque.size());
        for (FormData.FormValue formValue : deque) {
            if (formValue != null) {
                if (formValue.isFileItem()) {
                    throw new UnsupportedOperationException("文件上传支持可订阅 Aifei VIP");
                } else {
                    array.add(getStringValue(formValue.getValue()));
                }
            }
        }
        ret.put(key, array);
    }

    /**
     * 供 In.getBoolean(...) 使用
     */
    Boolean toBoolean(Object obj) {
        if (obj instanceof String) {
            String str = ((String) obj).trim();     // 与 aifei-db 不同，加了 trim()
            if ("true".equals(str) || "1".equals(str)) {
                return Boolean.TRUE;
            } else if ("false".equals(str) || "0".equals(str)) {
                return Boolean.FALSE;
            } else if (str.isEmpty()) {
                return null;
            } else {
                throw new IllegalArgumentException("Cannot convert String value \"" + str + "\" to Boolean. Only 'true', 'false', '1', and '0' are supported.");
            }
        }

        if (obj instanceof Number) {
            int n = ((Number) obj).intValue();
            if (n == 1) {
                return Boolean.TRUE;
            } else if (n == 0) {
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Cannot convert Number value " + n + " to Boolean. Only 0 and 1 are supported.");
            }
        }

        throw new IllegalArgumentException("Cannot convert type " + obj.getClass().getName() + " to Boolean.");
    }
}



