/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.core.Handler;
import cn.aifei.util.StrUtil;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 跨域支持
 */
public class CorsHandler extends Handler<In, Out> {

    final HttpString ACCESS_CONTROL_ALLOW_ORIGIN = new HttpString("Access-Control-Allow-Origin");
    final HttpString ACCESS_CONTROL_ALLOW_METHODS = new HttpString("Access-Control-Allow-Methods");
    final HttpString ACCESS_CONTROL_ALLOW_HEADERS = new HttpString("Access-Control-Allow-Headers");
    final HttpString ACCESS_CONTROL_ALLOW_CREDENTIALS = new HttpString("Access-Control-Allow-Credentials");

    Set<String> allowOriginWhitelist;

    String allowOrigin = "*";
    String allowMethods = "GET,POST,PUT,DELETE,PATCH,OPTIONS";
    String allowHeaders = "Content-Type,Authorization,X-Requested-With,Accept,Origin";
    String allowCredentials;

    /**
     * 配置 allowOrigin，默认值为 "*"
     *
     * <pre>
     * 例子：
     *   // 配置 aifei.cn 允许跨域访问
     *   setAllowOrigin("https://aifei.cn");
     * </pre>
     */
    public CorsHandler setAllowOrigin(String allowOrigin) {
        Objects.requireNonNull(allowOrigin, "allowOrigin can not be null.");
        this.allowOrigin = allowOrigin.trim();
        return this;
    }

    /**
     * 添加 allowOrigin 到白名单
     *
     * <pre>
     * 例子：
     *   // 添加 "https://aifei.cn" 与 "https://vip.aifei.cn" 到白名单，允许跨域访问
     *   addAllowOriginWhitelist("https://aifei.cn", "https://vip.aifei.cn");
     * </pre>
     */
    public CorsHandler addAllowOriginWhitelist(String... allowOrigins) {
        Objects.requireNonNull(allowOrigins, "allowOrigins can not be null.");
        if (allowOriginWhitelist == null) {
            allowOriginWhitelist = new HashSet<>();
        }
        for (String ao : allowOrigins) {
            if (StrUtil.notBlank(ao)) {
                allowOriginWhitelist.add(ao.trim());
            }
        }
        if (allowOriginWhitelist.isEmpty()) {
            allowOriginWhitelist = null;
        }
        return this;
    }

    /**
     * 配置 allowMethods，默认值为 "GET, POST, PUT, DELETE, PATCH, OPTIONS"
     */
    public CorsHandler setAllowMethods(String allowMethods) {
        Objects.requireNonNull(allowMethods, "allowMethods can not be null.");
        this.allowMethods = allowMethods.trim();
        return this;
    }

    /**
     * 配置 allowHeaders，默认值为 "Content-Type,Authorization,X-Requested-With,Accept,Origin"
     */
    public CorsHandler setAllowHeaders(String allowHeaders) {
        Objects.requireNonNull(allowHeaders, "allowHeaders can not be null.");
        this.allowHeaders = allowHeaders.trim();
        return this;
    }

    /**
     * 在原有 allowHeaders 之上添加 allowHeader
     *
     * <pre>
     * 例子：
     *    // 在原有 allowHeaders 默认值之上添加 "X-Token"
     *    addAllowHeaders("X-Token");
     *
     *    // 同时添加多个
     *    addAllowHeaders("Access-Token", "X-Token");
     * </pre>
     */
    public CorsHandler addAllowHeaders(String... allowHeaders) {
        Objects.requireNonNull(allowHeaders, "allowHeaders can not be null.");
        for (String ah : allowHeaders) {
            if (StrUtil.notBlank(ah)) {
                this.allowHeaders += "," + ah.trim();
            }
        }
        return this;
    }

    /**
     * 配置 allowCredentials，默认值为 null
     */
    public CorsHandler setAllowCredentials(Boolean allowCredentials) {
        this.allowCredentials = allowCredentials != null ? Boolean.toString(allowCredentials) : null;
        return this;
    }

    @Override
    public void handle(String path, In in, Out out) throws Exception {
        HttpServerExchange exchange = in.getExchange();
        HeaderMap responseHeaders = exchange.getResponseHeaders();

        // allowOrigin 支持白名单
        if (allowOriginWhitelist == null) {
            responseHeaders.put(ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
        } else {
            String origin = exchange.getRequestHeaders().getFirst(Headers.ORIGIN);
            if (allowOriginWhitelist.contains(origin)) {
                responseHeaders.put(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                // 告诉缓存服务器，响应头根据 Origin 动态变化，防止 CDN 把给 A 域名的响应缓存后发给了 B 域名
                responseHeaders.put(Headers.VARY, "Origin");
            } else {
                // 支持 "非浏览器" 客户端直接访问服务而无需关心跨域
                next.handle(path, in, out);
                return;
            }
        }

        responseHeaders.put(ACCESS_CONTROL_ALLOW_METHODS, allowMethods);
        responseHeaders.put(ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders);
        if (allowCredentials != null) {
            // 如果需要带 Cookie 须设置为 "true"，且 Origin 不能为 *
            responseHeaders.put(ACCESS_CONTROL_ALLOW_CREDENTIALS, allowCredentials);
        }

        if (Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            // 设置状态码 200。注意：有的框架习惯用 204 (No Content)，但在 CORS 场景下 200 兼容性最好
            exchange.setStatusCode(StatusCodes.OK);
            // 显式告诉客户端 Body 长度为 0 (可选，但规范)
            responseHeaders.put(Headers.CONTENT_LENGTH, "0");
            // 结束交换，发送响应
            exchange.endExchange();
        } else {
            next.handle(path, in, out);
        }
    }
}



