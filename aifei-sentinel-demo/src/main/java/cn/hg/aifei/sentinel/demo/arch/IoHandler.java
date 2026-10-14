/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.arch;

import cn.aifei.aop.Invocation;
import cn.aifei.core.Handler;
import cn.aifei.json.Json;
import cn.aifei.log.Log;
import cn.aifei.router.Action;
import cn.aifei.router.Router;
import cn.aifei.router.RouterKit;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.io.OutputStream;

/** 请求处理中枢：路由匹配 → 调用 action → 统一输出 JSON */
public class IoHandler extends Handler<In, Out> {

    Log log = Log.get(IoHandler.class);

    Router router = RouterKit.get().getRouter();
    TargetFactory targetFactory = new TargetFactory(true, true);

    @Override
    public void handle(String path, In in, Out out) throws Exception {
        Action action = router.getAction(path, in);
        if (action == null) {
            log.info("404 Action Not Found: " + path);
            handleOutput(in, Out.fail(OutCode.NOT_FOUND));
            return;
        }

        try {
            Object target = targetFactory.get(action.getTargetClass());
            Invocation invocation = new Invocation(action, target, in, out);
            invocation.invoke();

            Object returnValue = invocation.getReturnValue();
            if (returnValue instanceof Out) {
                out = (Out) returnValue;
            } else if (returnValue != null) {
                out = Out.of(returnValue);
            }

            handleOutput(in, out != null ? out : Out.fail("无返回数据"));

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            handleOutput(in, Out.fail(OutCode.SERVER_ERROR));
        }
    }

    private void handleOutput(In in, Out out) throws Exception {
        HttpServerExchange exchange = in.getExchange();
        OutputStream outputStream = exchange.getOutputStream();
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        Json.of(out).toJson(outputStream);
        outputStream.close();
    }
}
