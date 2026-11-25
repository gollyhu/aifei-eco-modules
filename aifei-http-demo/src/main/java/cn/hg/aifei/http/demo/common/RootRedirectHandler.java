/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.http.demo.common;

import cn.aifei.core.Handler;
import cn.aifei.demo.common.arch.In;
import cn.aifei.demo.common.arch.Out;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * 站点根路径重定向：访问 {@code /} 时以 302 + {@code Location: /http/demo/page} 跳转到演示主页。
 *
 * <p>本 Handler 只精确命中 {@link #ROOT_PATH}，其余路径一律交棒 {@code next}，因此放在责任链最前
 * 也不会影响 {@code /demo/echo} 与 Service 路由。</p>
 *
 * <p>本类由开发工程师寇戴码产出。</p>
 */
public class RootRedirectHandler extends Handler<In, Out> {

    /**
     * 接管的请求路径。
     */
    public static final String ROOT_PATH = "/";

    /**
     * 重定向目标：演示主页 action 的相对路径（随服务 host / 端口解析）。
     */
    public static final String REDIRECT_LOCATION = "/http/demo/page";

    /**
     * 处理一次请求：命中根路径时写 302 与 Location 并结束交换，否则交给责任链下游。
     *
     * @param path 请求路径
     * @param in 应用侧输入（含 Undertow exchange）
     * @param out 应用侧输出，本 Handler 不使用
     * @throws Exception 交棒下游时由下游抛出
     */
    @Override
    public void handle(String path, In in, Out out) throws Exception {
        if (!ROOT_PATH.equals(path)) {
            if (next != null) {
                next.handle(path, in, out);
            }
            return;
        }
        HttpServerExchange exchange = in.getExchange();
        exchange.getResponseHeaders().put(Headers.LOCATION, REDIRECT_LOCATION);
        exchange.setStatusCode(302);
        exchange.endExchange();
    }
}
