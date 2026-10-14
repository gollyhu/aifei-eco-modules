/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken.undertow;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.server.Dispatcher;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.exception.SaTokenException;
import io.undertow.server.HttpServerExchange;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Dispatcher 装饰器：上下文装配与清理的生命周期 */
public class SaTokenUndertowDispatcherTest {

    private SaTokenContext previousContext;

    @Before
    public void setUp() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
    }

    @After
    public void tearDown() {
        SaManager.getSaTokenContext().clearContext();
        SaManager.setSaTokenContext(previousContext);
    }

    @Test
    public void bindsContextDuringDispatchAndClearsAfterwards() {
        final boolean[] validInside = {false};

        SaTokenUndertowDispatcher<Input, Output> dispatcher =
                new SaTokenUndertowDispatcher<>(new RecordingDispatcher(validInside));
        dispatcher.init(new NoopHandler());
        dispatcher.dispatch(null, null);

        Assert.assertTrue("分发期间上下文应已装配", validInside[0]);
        Assert.assertFalse("分发结束后上下文应被清理", SaManager.getSaTokenContext().isValid());
    }

    @Test
    public void clearsContextEvenWhenDelegateThrows() {
        SaTokenUndertowDispatcher<Input, Output> dispatcher =
                new SaTokenUndertowDispatcher<>(new ThrowingDispatcher());
        dispatcher.init(new NoopHandler());

        try {
            dispatcher.dispatch(null, null);
            Assert.fail("delegate 异常应向上传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("delegate-failure", expected.getMessage());
        }
        Assert.assertFalse("异常路径同样应清理上下文", SaManager.getSaTokenContext().isValid());
    }

    @Test
    public void defaultErrorHandlerToleratesMissingExchange() {
        Assert.assertNull(SaTokenUndertowError.defaultHandler().handle(new SaTokenException("x")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullDelegate() {
        new SaTokenUndertowDispatcher<Input, Output>(null);
    }

    private static final class RecordingDispatcher
            implements Dispatcher<HttpServerExchange, Void, Input, Output> {

        private final boolean[] validInside;

        RecordingDispatcher(boolean[] validInside) {
            this.validInside = validInside;
        }

        @Override
        public void init(Handler<Input, Output> handler) {
        }

        @Override
        public void dispatch(HttpServerExchange exchange, Void unused) {
            validInside[0] = SaManager.getSaTokenContext().isValid();
        }
    }

    private static final class ThrowingDispatcher
            implements Dispatcher<HttpServerExchange, Void, Input, Output> {

        @Override
        public void init(Handler<Input, Output> handler) {
        }

        @Override
        public void dispatch(HttpServerExchange exchange, Void unused) {
            throw new IllegalStateException("delegate-failure");
        }
    }

    private static final class NoopHandler extends Handler<Input, Output> {

        @Override
        public void handle(String path, Input input, Output output) {
        }
    }
}
