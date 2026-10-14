/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.demo.common.arch;

import cn.aifei.core.Handler;
import cn.aifei.log.Log;
import cn.aifei.server.Dispatcher;
import io.undertow.server.HttpServerExchange;

/** 承接 Server 请求，包装为 In 派发给 Handler 链 */
public class IoDispatcher implements Dispatcher<HttpServerExchange, Void, In, Out> {

    static final Log log = Log.get(IoDispatcher.class);

    Handler<In, Out> handler;

    @Override
    public void init(Handler<In, Out> handler) {
        this.handler = handler;
    }

    @Override
    public void dispatch(HttpServerExchange exchange, Void v) {
        try {
            handler.handle(exchange.getRequestPath(), new In(exchange), null);
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            exchange.endExchange();
        }
    }
}
