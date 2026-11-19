/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 * 开发工程师 寇戴码（Enjoy 指令增量）
 */

package cn.hg.aifei.i18n.demo.arch;

import cn.aifei.core.Handler;
import cn.aifei.log.Log;
import cn.aifei.server.Dispatcher;
import io.undertow.server.HttpServerExchange;

/** IoDispatcher：承接 Server 请求，转换为 In / Out 派发给 Handler。 */
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
