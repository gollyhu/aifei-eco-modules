/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.core.Handler;
import cn.aifei.log.Log;
import cn.aifei.server.Dispatcher;
import io.undertow.server.HttpServerExchange;

/**
 * IoDispatcher 承接 Server 请求，并将请求参数转换为 Input、Output 派发给 Aifei Handler
 */
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
        } catch(Throwable e) {
            log.error(e.getMessage(), e);
            exchange.endExchange();
        }
    }
}

