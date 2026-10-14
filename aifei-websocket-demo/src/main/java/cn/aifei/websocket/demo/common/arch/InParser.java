/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.websocket.demo.common.arch;

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

/** 统一解析 query / json body / form 表单，query 优先于 body */
public class InParser {

    static final FormParserFactory formParserFactory = FormParserFactory
            .builder(false)
            .withDefaultCharset("UTF-8")
            .addParser(new FormEncodedDataDefinition())
            .build();

    public JSONObject parse(HttpServerExchange exchange, In in) {
        JSONObject queryPara = parseQueryPara(exchange);
        JSONObject bodyPara = parseBodyPara(exchange, in);

        if (bodyPara != null) {
            if (queryPara != null) {
                bodyPara.putAll(queryPara);
            }
            return bodyPara;
        }
        return queryPara != null ? queryPara : new JSONObject();
    }

    private JSONObject parseQueryPara(HttpServerExchange exchange) {
        Map<String, Deque<String>> queryParas = exchange.getQueryParameters();
        if (queryParas.isEmpty()) {
            return null;
        }
        JSONObject ret = new JSONObject(queryParas.size());
        for (Map.Entry<String, Deque<String>> e : queryParas.entrySet()) {
            String first = e.getValue().getFirst();
            ret.put(e.getKey(), getStringValue(first));
        }
        return ret;
    }

    private String getStringValue(String str) {
        return str == null || str.isEmpty() ? null : str;
    }

    private JSONObject parseBodyPara(HttpServerExchange exchange, In in) {
        if (Methods.GET.equals(exchange.getRequestMethod())) {
            return null;
        }
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType != null && contentType.contains("json")) {
            return parseJsonPara(exchange, in);
        }
        return parseFormPara(exchange);
    }

    private JSONObject parseJsonPara(HttpServerExchange exchange, In in) {
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

    private JSONObject parseFormPara(HttpServerExchange exchange) {
        FormData formData;
        try {
            FormDataParser parser = formParserFactory.createParser(exchange);
            if (parser == null) {
                return null;
            }
            formData = parser.parseBlocking();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        JSONObject ret = new JSONObject();
        for (String key : formData) {
            Deque<FormData.FormValue> deque = formData.get(key);
            if (deque != null && !deque.isEmpty()) {
                FormData.FormValue first = deque.getFirst();
                if (first == null) {
                    continue;
                }
                if (deque.size() > 1) {
                    JSONArray array = new JSONArray(deque.size());
                    for (FormData.FormValue v : deque) {
                        array.add(getStringValue(v.getValue()));
                    }
                    ret.put(key, array);
                } else {
                    handleObject(key, first.getValue(), ret);
                }
            }
        }
        return ret;
    }

    private void handleObject(String key, String value, JSONObject ret) {
        int index = key.indexOf('.');
        if (index == -1) {
            ret.put(key, getStringValue(value));
        } else {
            String prefix = key.substring(0, index);
            String field = key.substring(index + 1);
            JSONObject jo = ret.getJSONObject(prefix);
            if (jo == null) {
                jo = new JSONObject();
                ret.put(prefix, jo);
            }
            jo.put(field, getStringValue(value));
        }
    }

    Boolean toBoolean(Object obj) {
        if (obj instanceof String) {
            String str = ((String) obj).trim();
            if ("true".equals(str) || "1".equals(str)) {
                return Boolean.TRUE;
            } else if ("false".equals(str) || "0".equals(str)) {
                return Boolean.FALSE;
            } else if (str.isEmpty()) {
                return null;
            }
            throw new IllegalArgumentException("Cannot convert String value \"" + str + "\" to Boolean.");
        }
        if (obj instanceof Number) {
            int n = ((Number) obj).intValue();
            if (n == 1) {
                return Boolean.TRUE;
            } else if (n == 0) {
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Cannot convert Number value " + n + " to Boolean.");
        }
        throw new IllegalArgumentException("Cannot convert type " + obj.getClass().getName() + " to Boolean.");
    }
}
