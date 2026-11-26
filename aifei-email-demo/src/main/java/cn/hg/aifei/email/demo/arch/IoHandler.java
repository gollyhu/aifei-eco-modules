/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.email.demo.arch;

import cn.aifei.aop.Invocation;
import cn.aifei.argument.ArgumentException;
import cn.aifei.core.Handler;
import cn.aifei.enjoy.Engine;
import cn.aifei.json.Json;
import cn.aifei.log.Log;
import cn.aifei.router.Action;
import cn.aifei.router.Router;
import cn.aifei.router.RouterKit;
import cn.aifei.util.PathUtil;
import cn.aifei.util.PropKit;
import cn.aifei.util.StrUtil;
import io.undertow.server.HttpServerExchange;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/** 路由处理器：匹配 @Path action、调用、统一 Out，再渲染 view 或输出 JSON。 */
public class IoHandler extends Handler<In, Out> {

    Log log = Log.get(IoHandler.class);
    Engine engine;
    Router router = RouterKit.get().getRouter();
    TargetFactory targetFactory = new TargetFactory(true, true);

    public IoHandler(String baseViewPath) {
        if (StrUtil.isBlank(baseViewPath)) {
            throw new IllegalArgumentException("baseViewPath 不能为空");
        }
        this.engine = Engine.createIfAbsent("AIFEI", e -> {
            e.setDevMode(PropKit.getBoolean("enjoyDevMode", false));
            e.setBaseTemplatePath(PathUtil.getWebRootPath().resolve(baseViewPath).toString());
        });
    }

    @Override
    public void handle(String path, In in, Out out) throws Exception {
        Action action = router.getAction(path, in);
        if (action == null) {
            handleOutput(in, Out.fail(OutCode.NOT_FOUND.msg()));
            return;
        }
        try {
            Object target = targetFactory.get(action.getTargetClass());
            Invocation invocation = new Invocation(action, target, in, out);
            invocation.invoke();
            Object returnValue = invocation.getReturnValue();
            if (returnValue instanceof Out) {
                out = (Out) returnValue;
            } else if (returnValue instanceof String && ((String) returnValue).endsWith(".html")) {
                out = new Out().view((String) returnValue);
            } else if (returnValue != null) {
                out = Out.of(returnValue);
            }
            handleEnjoyView(out, action);
            handleOutput(in, out != null ? out : Out.fail("无返回数据"));
        } catch (ArgumentException ae) {
            handleOutput(in, Out.fail("请求参数错误: " + ae.getArgumentInfoAndMessage()));
        } catch (Exception e) {
            Throwable t = e;
            if (e instanceof InvocationTargetException) {
                Throwable target = ((InvocationTargetException) e).getTargetException();
                t = target == null ? e : target;
            }
            log.error(t.getMessage(), t);
            handleOutput(in, Out.fail(OutCode.SERVER_ERROR.msg()));
        }
    }

    private void handleOutput(In in, Out out) throws Exception {
        HttpServerExchange exchange = in.getExchange();
        OutputStream outputStream = exchange.getOutputStream();
        String view = out == null ? null : out.view;
        if (view != null) {
            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, ContentType.HTML.value);
            Object data = out.getData();
            Map<?, ?> map = data instanceof Map ? (Map<?, ?>) data : null;
            engine.getTemplate(view).render(map, outputStream);
        } else {
            exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, ContentType.JSON.value);
            Json.of(out).toJson(outputStream);
        }
        outputStream.close();
    }

    private void handleEnjoyView(Out out, Action action) {
        if (out != null && out.view != null && !out.view.startsWith("/")) {
            out.view(action.getTargetPath().length() == 1 ? "/" + out.view : action.getTargetPath() + "/" + out.view);
        }
    }
}
