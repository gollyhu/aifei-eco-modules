/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.arch;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormEncodedDataDefinition;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import java.io.IOException;
import java.util.Deque;
import java.util.Map;

/** demo 请求参数解析：query + json body + form 表单（无文件上传）。 */
public class InParser {

    static final FormParserFactory FORM_PARSER_FACTORY = FormParserFactory.builder(false)
            .withDefaultCharset("UTF-8")
            .addParser(new FormEncodedDataDefinition())
            .build();

    /** 解析后返回非 null，避免重复解析。 */
    public JSONObject parse(HttpServerExchange exchange, In in) {
        JSONObject query = parseQuery(exchange);
        JSONObject body = parseBody(exchange, in);
        if (body != null) {
            if (query != null) {
                body.putAll(query);
            }
            return body;
        }
        return query != null ? query : new JSONObject();
    }

    private static JSONObject parseQuery(HttpServerExchange exchange) {
        Map<String, Deque<String>> query = exchange.getQueryParameters();
        if (query.isEmpty()) {
            return null;
        }
        JSONObject ret = new JSONObject(query.size());
        for (Map.Entry<String, Deque<String>> entry : query.entrySet()) {
            String first = entry.getValue().getFirst();
            ret.put(entry.getKey(), first == null || first.isEmpty() ? null : first);
        }
        return ret;
    }

    private static JSONObject parseBody(HttpServerExchange exchange, In in) {
        if (Methods.GET.equals(exchange.getRequestMethod())) {
            return null;
        }
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType != null && contentType.contains("json")) {
            if (exchange.getRequestContentLength() == 0) {
                return null;
            }
            Object json = JSON.parse(exchange.getInputStream(), JSONFactory.createReadContext());
            if (json instanceof JSONObject) {
                return (JSONObject) json;
            }
            if (json instanceof JSONArray) {
                in.arrayData = (JSONArray) json;
            }
            return null;
        }
        FormDataParser parser = FORM_PARSER_FACTORY.createParser(exchange);
        if (parser == null) {
            return null;
        }
        try {
            FormData formData = parser.parseBlocking();
            JSONObject ret = new JSONObject();
            for (String key : formData) {
                Deque<FormData.FormValue> deque = formData.get(key);
                if (deque == null || deque.getFirst() == null) {
                    ret.put(key, null);
                    continue;
                }
                String value = deque.getFirst().getValue();
                ret.put(key, value == null || value.isEmpty() ? null : value);
            }
            return ret;
        } catch (IOException e) {
            throw new IllegalStateException("parse form body failed", e);
        }
    }

    /** 供 In.getBoolean 使用。 */
    Boolean toBoolean(Object obj) {
        if (obj instanceof String) {
            String str = ((String) obj).trim();
            if ("true".equals(str) || "1".equals(str)) {
                return Boolean.TRUE;
            }
            if ("false".equals(str) || "0".equals(str)) {
                return Boolean.FALSE;
            }
            if (str.isEmpty()) {
                return null;
            }
            throw new IllegalArgumentException("Cannot convert to boolean: " + str);
        }
        if (obj instanceof Number) {
            int n = ((Number) obj).intValue();
            if (n == 1) {
                return Boolean.TRUE;
            }
            if (n == 0) {
                return Boolean.FALSE;
            }
        }
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
        throw new IllegalArgumentException("Cannot convert to boolean: " + obj);
    }
}
