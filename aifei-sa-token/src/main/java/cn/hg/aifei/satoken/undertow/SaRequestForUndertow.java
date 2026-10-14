/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken.undertow;

import cn.aifei.core.Input;
import cn.dev33.satoken.context.model.SaRequest;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link SaRequest} 的 Undertow 桥接实现。
 */
final class SaRequestForUndertow implements SaRequest {

    @Override
    public Object getSource() {
        return SaTokenUndertowContext.getExchange();
    }

    @Override
    public String getParam(String name) {
        Input input = SaTokenUndertowContext.getInput();
        if (input != null) {
            return input.getStr(name);
        }
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange == null) {
            return null;
        }
        Deque<String> values = exchange.getQueryParameters().get(name);
        return values != null && !values.isEmpty() ? values.peekFirst() : null;
    }

    @Override
    public Collection<String> getParamNames() {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        return exchange != null ? exchange.getQueryParameters().keySet() : Collections.<String>emptyList();
    }

    @Override
    public Map<String, String> getParamMap() {
        Map<String, String> result = new LinkedHashMap<>();
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange == null) {
            return result;
        }
        for (Map.Entry<String, Deque<String>> entry : exchange.getQueryParameters().entrySet()) {
            Deque<String> values = entry.getValue();
            if (values != null && !values.isEmpty()) {
                result.put(entry.getKey(), values.peekFirst());
            }
        }
        return result;
    }

    @Override
    public String getHeader(String name) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        return exchange != null ? exchange.getRequestHeaders().getFirst(name) : null;
    }

    @Override
    public String getCookieValue(String name) {
        return getCookieFirstValue(name);
    }

    @Override
    public String getCookieFirstValue(String name) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange == null) {
            return null;
        }
        Cookie cookie = exchange.getRequestCookie(name);
        return cookie != null ? cookie.getValue() : null;
    }

    @Override
    public String getCookieLastValue(String name) {
        return getCookieFirstValue(name);
    }

    @Override
    public String getRequestPath() {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        return exchange != null ? exchange.getRequestPath() : null;
    }

    @Override
    public String getUrl() {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        return exchange != null ? exchange.getRequestURL() : null;
    }

    @Override
    public String getMethod() {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        return exchange != null ? exchange.getRequestMethod().toString() : null;
    }

    @Override
    public String getHost() {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        return exchange != null ? exchange.getHostName() : null;
    }

    @Override
    public Object forward(String path) {
        throw new UnsupportedOperationException("aifei Undertow 不支持服务端 forward。");
    }
}
