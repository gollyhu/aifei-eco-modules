/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.undertow;

import cn.aifei.core.Input;
import io.undertow.server.HttpServerExchange;

/**
 * 请求线程上下文持有器。
 *
 * <p>把当前请求的 {@link HttpServerExchange} 与 aifei 已解析的 {@link Input} 绑定到线程，
 * 供 Sa-Token 的请求/响应/存储桥接对象读取。</p>
 *
 * <p>必须在请求结束时清理：Undertow 复用请求线程，残留绑定会串入下一个请求。</p>
 */
final class SaTokenUndertowContext {

    private static final ThreadLocal<HttpServerExchange> EXCHANGE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Input> INPUT_HOLDER = new ThreadLocal<>();

    static void setExchange(HttpServerExchange exchange) {
        EXCHANGE_HOLDER.set(exchange);
    }

    static HttpServerExchange getExchange() {
        return EXCHANGE_HOLDER.get();
    }

    static void clearExchange() {
        EXCHANGE_HOLDER.remove();
    }

    /**
     * 绑定 aifei 输入对象。
     *
     * <p>由 Dispatcher 包装的 Handler 在进入业务前调用：此时 aifei 才刚创建出 Input 实例。</p>
     */
    static void setInput(Input input) {
        INPUT_HOLDER.set(input);
    }

    static Input getInput() {
        return INPUT_HOLDER.get();
    }

    static void clearInput() {
        INPUT_HOLDER.remove();
    }

    private SaTokenUndertowContext() {}
}
