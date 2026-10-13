/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.undertow;

import cn.dev33.satoken.context.model.SaCookie;
import cn.dev33.satoken.context.model.SaResponse;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

/**
 * {@link SaResponse} 的 Undertow 桥接实现。
 */
final class SaResponseForUndertow implements SaResponse {

    @Override
    public Object getSource() {
        return SaTokenUndertowContext.getExchange();
    }

    @Override
    public void addCookie(SaCookie cookie) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange == null || cookie == null) {
            return;
        }

        CookieImpl target = new CookieImpl(cookie.getName(), cookie.getValue());
        target.setPath(cookie.getPath());
        target.setDomain(cookie.getDomain());
        if (cookie.getMaxAge() >= 0) {
            target.setMaxAge(cookie.getMaxAge());
        }
        if (cookie.getHttpOnly() != null) {
            target.setHttpOnly(cookie.getHttpOnly());
        }
        if (cookie.getSecure() != null) {
            target.setSecure(cookie.getSecure());
        }
        if (cookie.getSameSite() != null) {
            target.setSameSiteMode(cookie.getSameSite());
        }
        exchange.setResponseCookie(target);
    }

    @Override
    public SaResponse setStatus(int sc) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange != null) {
            exchange.setStatusCode(sc);
        }
        return this;
    }

    @Override
    public SaResponse setHeader(String name, String value) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange != null) {
            exchange.getResponseHeaders().put(HttpString.tryFromString(name), value);
        }
        return this;
    }

    @Override
    public SaResponse addHeader(String name, String value) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange != null) {
            exchange.getResponseHeaders().add(HttpString.tryFromString(name), value);
        }
        return this;
    }

    /** 发送重定向 */
    @Override
    public Object redirect(String url) {
        HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
        if (exchange == null) {
            return null;
        }
        exchange.setStatusCode(StatusCodes.FOUND);
        exchange.getResponseHeaders().put(Headers.LOCATION, url);
        return null;
    }
}
