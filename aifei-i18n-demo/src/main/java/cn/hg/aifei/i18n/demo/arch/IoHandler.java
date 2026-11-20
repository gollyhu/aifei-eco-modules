/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.demo.arch;

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
import cn.hg.aifei.i18n.enjoy.directive.DirectivePageState;
import cn.hg.aifei.i18n.enjoy.directive.DirectiveRegistrar;
import io.undertow.server.HttpServerExchange;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * IoHandler：控制 action 调用流程，定制 Output 处理方式（demo 简化版）。
 *
 * <p>与 excel-demo 的两处关键差异：</p>
 * <ol>
 *   <li>构造引擎后调用 {@link DirectiveRegistrar#register(Engine)} 一次，注册三条 Enjoy 指令；</li>
 *   <li>渲染 view 前先 {@link DirectivePageState#clear()}，render 置于 try/finally 并 finally 再清，
 *       保证同线程连续渲染不继承上次页面级 lang/dir（AC-INC-12，异常路径同样清理）。</li>
 * </ol>
 */
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
        // 三条指令集中注册一次（keepLineBlank=true）
        DirectiveRegistrar.register(this.engine);
    }

    @Override
    public void handle(String path, In in, Out out) throws Exception {
        Action action = router.getAction(path, in);
        if (action == null) {
            if (log.isInfoEnabled()) {
                log.info("404 Action Not Found: " + path);
            }
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
            } else if (returnValue instanceof String && ((String) returnValue).endsWith(".html")) {
                out = new Out().view((String) returnValue);
            } else if (returnValue != null) {
                out = Out.of(returnValue);
            }

            handleEnjoyView(out, action);
            handleOutput(in, out != null ? out : Out.fail("无返回数据"));
        } catch (ArgumentException ae) {
            String msg = "请求参数错误: " + ae.getArgumentInfoAndMessage();
            log.info(msg, ae);
            handleOutput(in, Out.fail(msg));
        } catch (Exception e) {
            Throwable t = e;
            if (e instanceof InvocationTargetException) {
                Throwable target = ((InvocationTargetException) e).getTargetException();
                if (target != null) {
                    t = target;
                }
            }
            try {
                log.error(t.getMessage(), t);
                handleOutput(in, Out.fail(OutCode.SERVER_ERROR));
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    private void handleOutput(In in, Out out) throws Exception {
        HttpServerExchange exchange = in.getExchange();
        OutputStream outputStream = exchange.getOutputStream();

        String view = getView(out);
        if (view != null) {
            setContentType(exchange, ContentType.HTML);
            Object d = out.getData();
            Map<?, ?> data = d instanceof Map ? (Map<?, ?>) d : null;
            // 页面级 lang/dir 是 ThreadLocal 状态：渲染前清 + finally 清，防同线程串页
            DirectivePageState.clear();
            try {
                engine.getTemplate(view).render(data, outputStream);
            } finally {
                DirectivePageState.clear();
            }
            outputStream.close();
            return;
        }

        setContentType(exchange, ContentType.JSON);
        Json.of(out).toJson(outputStream);
        outputStream.close();
    }

    private void setContentType(HttpServerExchange exchange, ContentType contentType) {
        exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, contentType.value);
    }

    private void handleEnjoyView(Out out, Action action) {
        if (out != null && out.view != null && !out.view.startsWith("/")) {
            if (action.getTargetPath().length() == 1) {
                out.view("/" + out.view);
            } else {
                out.view(action.getTargetPath() + "/" + out.view);
            }
        }
    }

    private String getView(Out out) {
        return out != null ? out.view : null;
    }
}
