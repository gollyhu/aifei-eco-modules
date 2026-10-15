/*
 * Copyright 2011-2035 詹波 (aifei.cn)
 *
 * https://aifei.cn
 */

package cn.aifei.demo.common.arch;

import cn.aifei.aop.Invocation;
import cn.aifei.argument.ArgumentException;
import cn.aifei.util.PathUtil;
import cn.aifei.core.Handler;
import cn.aifei.enjoy.Engine;
import cn.aifei.json.Json;
import cn.aifei.log.Log;
import cn.aifei.router.Action;
import cn.aifei.router.Router;
import cn.aifei.router.RouterKit;
import cn.aifei.util.PropKit;
import cn.aifei.util.StrUtil;
import io.undertow.server.HttpServerExchange;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * IoHandler。控制 action 调用流程，定制 Output 处理方式。
 */
public class IoHandler extends Handler<In, Out> {

    Log log = Log.get(IoHandler.class);

    Engine engine;
    ActionPrinter actionPrinter;
    Router router = RouterKit.get().getRouter();
    TargetFactory targetFactory = new TargetFactory(true, true);

    public IoHandler(boolean printAction) {
        this(printAction, "view");
    }

    public IoHandler(boolean printAction, String baseViewPath) {
        if (StrUtil.isBlank(baseViewPath)) {
            throw new IllegalArgumentException("baseViewPath 不能为空");
        }
        if (printAction) {
            this.actionPrinter = new ActionPrinter();
        }
        this.engine = Engine.createIfAbsent("AIFEI", e -> {
            e.setDevMode(PropKit.getBoolean("enjoyDevMode", false));
            e.setBaseTemplatePath(PathUtil.getWebRootPath().resolve(baseViewPath).toString());
        });
    }

    /**
     * handle 主流程：
     * 1: 匹配路由获取 action：action = router.getAction(...)
     * 2: 调用 action：new Invocation(action, ...).invoke()
     * 3: 将输出统一成 Out 对象：优先 returnValue，而后 argument 注入 Out，最后上游 Out
     * 4: 处理统一后的 Out 对象：handleOutput(out)
     */
    @Override
    public void handle(String path, In in, Out out) throws Exception {
        // 匹配路径获取 action
        Action action = router.getAction(path, in);
        if (action == null) {
            if (log.isInfoEnabled()) {
                log.info("404 Action Not Found: " + path);
            }
            // in.getExchange().setStatusCode(404);
            handleOutput(in, Out.fail(OutCode.NOT_FOUND));
            return;
        }

        try {
            // 调用 action
            Object target = targetFactory.get(action.getTargetClass());
            Invocation invocation = new Invocation(action, target, in, out);
            if (actionPrinter != null) {
                actionPrinter.print(action, in);    // 打印 action 信息
            }
            invocation.invoke();

            // 将输出统一成 Out 对象，企业版见 Aifei VIP 订阅
            Object returnValue = invocation.getReturnValue();
            if (returnValue instanceof Out) {
                out = (Out) returnValue;
            } else if (returnValue instanceof String && ((String) returnValue).endsWith(".html")) {
                out = new Out().view((String) returnValue);
            } else if (returnValue != null) {
                out = Out.of(returnValue);
            }

            // 处理统一后的 Out 对象
            handleEnjoyView(out, action);
            handleOutput(in, out != null ? out : Out.fail("无返回数据"));

        } catch (ArgumentException ae) {
            String msg = "请求参数错误: " + ae.getArgumentInfoAndMessage();
            log.info(msg, ae);
            handleOutput(in, Out.fail(msg));

        } catch (Exception e) {
            Throwable t;
            if (e instanceof InvocationTargetException) {
                t = ((InvocationTargetException) e).getTargetException();
                if (t == null) {t = e;}
            } else {
                t = e;
            }

            // 处理系统异常和其它异常，message "不可" 输出到客户端。error 日志
            try {
                log.error(t.getMessage(), t);
                handleOutput(in, Out.fail(OutCode.SERVER_ERROR));
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
        }
    }

    /**
     * 处理统一后的 Out 对象
     * outputStream.close() 通知后续 exchange.endExchange()，注意 SSE 不能关闭流
     */
    private void handleOutput(In in, Out out) throws Exception {
        HttpServerExchange exchange = in.getExchange();
        OutputStream outputStream = exchange.getOutputStream();

        // 处理 enjoy 模板渲染
        String view = getView(out);
        if (view != null) {
            setContentType(exchange, ContentType.HTML);
            Object d = out.getData();
            Map<?, ?> data = d instanceof Map ? (Map<?, ?>) d : null;
            engine.getTemplate(view).render(data, outputStream);
            outputStream.close();
            return;
        }

        // 处理 json 响应
        setContentType(exchange, ContentType.JSON);
        // 此处可通过 fastjson2 ObjectWriter 与 ObjectReader 定制序列化和反序列化。例如处理驼峰下划线转换
        Json.of(out).modelAsRow(getModelAsRow(out)).toJson(outputStream);
        outputStream.close();
    }

    private void setContentType(HttpServerExchange exchange, ContentType contentType) {
        exchange.getResponseHeaders().put(io.undertow.util.Headers.CONTENT_TYPE, contentType.value);
    }

    // 如果 view 不以 "/" 为前缀，则添加 targetPath 为前缀。按模块规划 targetPath 可减少代码量
    private void handleEnjoyView(Out out, Action action) {
        if (out != null && out.view != null && !out.view.startsWith("/")) {
            if (action.getTargetPath().length() == 1) { // targetPath 为 "/" 时不追加它为前缀
                out.view("/" + out.view);
            } else {
                out.view(action.getTargetPath() + "/" + out.view);
            }
        }
    }

    private boolean getModelAsRow(Out out) {
        return out != null && out.modelAsRow;
    }

    private String getView(Out out) {
        return out != null ? out.view : null;
    }
}


