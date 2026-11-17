/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.excel.demo.arch;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONFactory;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;
import java.util.Map;

/**
 * InParser：请求参数解析（demo 简化版，支持 query 参数、json 请求体与 form 表单，不含文件上传）。
 */
public class InParser {

    static final FormParserFactory formParserFactory = FormParserFactory
            .builder(false)
            .withDefaultCharset("UTF-8")
            .addParser(new FormEncodedDataDefinition())
            .addParser(new MultiPartParserDefinition())
            .build();

    /**
     * 解析数据，注意返回值必须为 "非 null"，避免重复解析。
     */
    public JSONObject parse(HttpServerExchange exchange, In in) {
        JSONObject queryPara = parseQueryPara(exchange);
        JSONObject bodyPara = parseBodyPara(exchange, in);

        // 两类解析结果允许为 null，但必须确保返回非 null
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
     * 解析 http 请求体数据，包括 json 数据和 form 表单数据。HTTP 规范：GET 请求忽略 body
     */
    JSONObject parseBodyPara(HttpServerExchange exchange, In in) {
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

    // 解析 form 表单数据（支持平铺键值与 multipart 文件项；不支持数组键）
    JSONObject parseFormPara(HttpServerExchange exchange, In in) {
        FormData formData;
        try {
            // 坑：不能使用 try-with-resources 关闭 parser，否则 InputStream 被关闭
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
            if (deque == null) {
                continue;
            }
            FormData.FormValue first = deque.getFirst();
            if (first == null) {
                ret.put(key, null);
                continue;
            }
            if (first.isFileItem()) {
                in.uploadFileName = first.getFileName();
                try {
                    in.uploadBytes = readAll(first.getFileItem().getInputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                ret.put(key, first.getFileName());
            } else {
                ret.put(key, getStringValue(first.getValue()));
            }
        }

        return ret;
    }

    private static byte[] readAll(InputStream in) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 供 In.getBoolean(...) 使用
     */
    Boolean toBoolean(Object obj) {
        if (obj instanceof String) {
            String str = ((String) obj).trim();
            if ("true".equals(str) || "1".equals(str)) {
                return Boolean.TRUE;
            } else if ("false".equals(str) || "0".equals(str)) {
                return Boolean.FALSE;
            } else if (str.isEmpty()) {
                return null;
            } else {
                throw new IllegalArgumentException("Cannot convert String value \"" + str
                        + "\" to Boolean. Only 'true', 'false', '1', and '0' are supported.");
            }
        }

        if (obj instanceof Number) {
            int n = ((Number) obj).intValue();
            if (n == 1) {
                return Boolean.TRUE;
            } else if (n == 0) {
                return Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Cannot convert Number value " + n
                        + " to Boolean. Only 0 and 1 are supported.");
            }
        }

        throw new IllegalArgumentException("Cannot convert type " + obj.getClass().getName() + " to Boolean.");
    }
}
