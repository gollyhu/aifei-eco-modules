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
 * <p>这是本 SDK 的核心接线点。选择 Dispatcher 层而非拦截器层，原因是
 * Dispatcher 的入参即为 {@link HttpServerExchange}：请求元信息（请求头、Cookie、方法、路径）
 * 与响应写能力（状态码、响应头、Cookie）在此处全部可得，从而对消费方零侵入——
 * 不必改造自有 HIO，也不必为 Sa-Token 编写适配类。</p>
 *
 * <p>用法（仅需改动装配处的这一行）：</p>
 * <pre>
 * settings.setServer(new UndertowServer(), new SaTokenUndertowDispatcher&lt;&gt;(new IoDispatcher()));
 * </pre>
 *
 * <p>请求周期内的动作：</p>
 * <ol>
 *   <li>绑定 exchange，并装配 Sa-Token 的 Request / Response / Storage 三个模型对象；</li>
 *   <li>进入业务 Handler 前补充绑定 aifei 已创建的 Input，使请求体参数可被读取；</li>
 *   <li>请求结束后统一清理线程变量，避免线程复用导致上下文串号。</li>
 * </ol>
 *
 * <p><b>前置条件</b>：必须先完成 {@code SaTokenAifeiPlugin} 装配，否则
 * {@code SaManager.getSaTokenContext()} 仍为默认实现，装配上下文时会抛出异常。</p>
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
     * <p>Input 由被包装的 Dispatcher 在分发时创建，故只能在此处捕获。</p>
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
