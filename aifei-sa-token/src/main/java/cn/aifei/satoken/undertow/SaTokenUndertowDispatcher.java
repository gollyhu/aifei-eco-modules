/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.undertow;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.server.Dispatcher;
import cn.dev33.satoken.SaManager;
import io.undertow.server.HttpServerExchange;

/**
 * 为 aifei 的 Undertow Dispatcher 增加 Sa-Token 上下文能力的装饰器。
 *
 * <p>用法（仅需改动装配处的这一行）：</p>
 * <pre>
 * settings.setServer(new UndertowServer(), new SaTokenUndertowDispatcher&lt;&gt;(new IoDispatcher()));
 * </pre>
 *
 * @param <I> aifei 输入类型
 * @param <O> aifei 输出类型
 */
public class SaTokenUndertowDispatcher<I extends Input, O extends Output>
        implements Dispatcher<HttpServerExchange, Void, I, O> {

    private final Dispatcher<HttpServerExchange, Void, I, O> delegate;

    /**
     * 包装目标 Dispatcher。
     *
     * @param delegate 原始 Dispatcher，不能为空
     * @throws IllegalArgumentException delegate 为空
     */
    public SaTokenUndertowDispatcher(Dispatcher<HttpServerExchange, Void, I, O> delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate can not be null.");
        }
        this.delegate = delegate;
    }

    /**
     * 转交初始化，并在业务 Handler 执行期间补充绑定 Input。
     *
     * @param handler 业务 Handler
     */
    @Override
    public void init(Handler<I, O> handler) {
        delegate.init(new Handler<I, O>() {
            @Override
            public void handle(String path, I input, O output) throws Exception {
                SaTokenUndertowContext.setInput(input);
                try {
                    handler.handle(path, input, output);
                } finally {
                    SaTokenUndertowContext.clearInput();
                }
            }
        });
    }

    /**
     * 分发请求，并在整个分发周期内维护 Sa-Token 上下文。
     *
     * @param exchange 当前 Undertow 请求交换对象
     * @param unused 占位参数，来自 Server 的第二个泛型参数
     */
    @Override
    public void dispatch(HttpServerExchange exchange, Void unused) {
        SaTokenUndertowContext.setExchange(exchange);
        try {
            SaManager.getSaTokenContext().setContext(
                    new SaRequestForUndertow(),
                    new SaResponseForUndertow(),
                    new SaStorageForUndertow(exchange));
            delegate.dispatch(exchange, unused);
        } finally {
            SaManager.getSaTokenContext().clearContext();
            SaTokenUndertowContext.clearInput();
            SaTokenUndertowContext.clearExchange();
        }
    }
}
