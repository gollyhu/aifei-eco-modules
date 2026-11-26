/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sms.demo.mock;

import cn.hg.aifei.sms.aifei.SmsIo;
import cn.hg.aifei.sms.demo.arch.In;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.Headers;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * demo 回执传输桥接：从 Undertow 交换读取回调参数 / 原始 body，并以指定状态码写回 JSON 响应。
 */
public final class SmsIoImpl implements SmsIo<In> {

    private static final AttachmentKey<String> RAW_BODY = AttachmentKey.create(String.class);

    @Override
    public Map<String, String> params(In in) {
        HttpServerExchange exchange = in.getExchange();
        Map<String, String> params = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Deque<String>> entry : exchange.getQueryParameters().entrySet()) {
            String value = entry.getValue().getFirst();
            if (value != null) {
                params.put(entry.getKey(), value);
            }
        }
        String body = body(in);
        if (body == null || body.isEmpty()) {
            return params;
        }
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType != null && contentType.contains("json")) {
            JSONObject json = JSON.parseObject(body);
            if (json != null) {
                for (Map.Entry<String, Object> entry : json.entrySet()) {
                    params.put(entry.getKey(), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                }
            }
        } else {
            for (String pair : body.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                try {
                    params.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
                } catch (IOException e) {
                    throw new IllegalStateException("decode callback params failed", e);
                }
            }
        }
        return params;
    }

    @Override
    public String body(In in) {
        HttpServerExchange exchange = in.getExchange();
        String cached = exchange.getAttachment(RAW_BODY);
        if (cached != null) {
            return cached;
        }
        if (exchange.getRequestContentLength() == 0) {
            exchange.putAttachment(RAW_BODY, "");
            return "";
        }
        exchange.startBlocking();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        try {
            while ((n = exchange.getInputStream().read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new IllegalStateException("read callback body failed", e);
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        exchange.putAttachment(RAW_BODY, text);
        return text;
    }

    @Override
    public void reply(In in, int status, String json) {
        HttpServerExchange exchange = in.getExchange();
        exchange.setStatusCode(status);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.startBlocking();
        try {
            OutputStream out = exchange.getOutputStream();
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (IOException e) {
            throw new IllegalStateException("write callback reply failed", e);
        }
    }
}
