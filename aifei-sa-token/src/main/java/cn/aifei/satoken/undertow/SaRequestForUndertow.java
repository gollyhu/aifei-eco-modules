/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.undertow;

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
 *
 * <p>参数读取有两条来源，按优先级依次尝试：</p>
 * <ol>
 *   <li>aifei 已解析的 {@link Input}：可覆盖 JSON body 等场景，是读取「请求体参数」的唯一途径；</li>
 *   <li>Undertow 原生查询参数：仅在 Input 尚未绑定（如静态资源等非 action 请求）时兜底。</li>
 * </ol>
 *
 * <p><b>已知限制</b>：{@code Input} 接口不支持枚举参数名，故 {@link #getParamNames()} 与
 * {@link #getParamMap()} 只能读取查询串，不含请求体参数，与 {@link #getParam(String)} 的数据源不完全一致。</p>
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
