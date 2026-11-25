/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.common;

import cn.aifei.core.Handler;
import cn.aifei.demo.common.arch.In;
import cn.aifei.demo.common.arch.Out;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/** 根路径 302 重定向到演示主页面，其余路径透传。 */
public class RootRedirectHandler extends Handler<In, Out> {

    private static final String HOME = "/mq/demo/page";

    @Override
    public void handle(String path, In in, Out out) throws Exception {
        if (!"/".equals(path)) {
            if (next != null) {
                next.handle(path, in, out);
            }
            return;
        }
        HttpServerExchange exchange = in.getExchange();
        exchange.setStatusCode(302);
        exchange.getResponseHeaders().put(Headers.LOCATION, HOME);
        exchange.endExchange();
    }
}
