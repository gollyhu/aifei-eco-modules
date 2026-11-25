/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.i18n.web;

import cn.aifei.core.Handler;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.hg.aifei.i18n.internal.I18nConfig;
import cn.hg.aifei.i18n.internal.I18nEngine;
import cn.hg.aifei.i18n.internal.I18nHolder;
import cn.hg.aifei.i18n.internal.LocaleContext;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Web 入口 Locale 绑定与清理单测。
 */
public class I18nHandlerTest {

    private final Input input = (Input) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{Input.class}, (proxy, method, args) -> null);
    private final Output output = (Output) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{Output.class}, (proxy, method, args) -> null);

    @Before
    public void setUp() {
        Properties props = new Properties();
        props.setProperty(I18nConfig.KEY_DEFAULT_LOCALE, "en");
        props.setProperty(I18nConfig.KEY_LOCALES, "zh-CN,en");
        I18nConfig config = I18nConfig.parse(props);
        I18nHolder.install(new I18nEngine(config, new ArrayList<cn.hg.aifei.i18n.MessageSource>()));
        LocaleContext.clear();
    }

    @After
    public void tearDown() {
        LocaleContext.clear();
        I18nHolder.clear();
    }

    @Test
    public void bindsLocaleForDownstream() throws Exception {
        final String[] observed = new String[1];
        TestableHandler handler = new TestableHandler(in -> "zh-CN,en;q=0.8");
        handler.setNext(new Handler<Input, Output>() {
            @Override
            public void handle(String path, Input in, Output out) {
                observed[0] = LocaleContext.get();
            }
        });
        handler.handle("/", input, output);
        assertEquals("zh-CN", observed[0]);
    }

    @Test
    public void clearsLocaleAfterRequest() throws Exception {
        TestableHandler handler = new TestableHandler(in -> "zh-CN");
        handler.handle("/", input, output);
        assertNull("请求结束应清理上下文", LocaleContext.get());
    }

    @Test
    public void sameThreadTwoRequestsDoNotBleed() throws Exception {
        final String[] observed = new String[2];
        final int[] extractIndex = new int[1];
        TestableHandler handler = new TestableHandler(in -> extractIndex[0]++ == 0 ? "zh-CN" : "en");
        handler.setNext(new Handler<Input, Output>() {
            private int index;
            @Override
            public void handle(String path, Input in, Output out) {
                observed[index++] = LocaleContext.get();
            }
        });
        handler.handle("/", input, output);
        handler.handle("/", input, output);
        assertEquals("zh-CN", observed[0]);
        assertEquals("en", observed[1]);
    }

    @Test
    public void clearsOnDownstreamException() {
        TestableHandler handler = new TestableHandler(in -> "zh-CN");
        handler.setNext(new Handler<Input, Output>() {
            @Override
            public void handle(String path, Input in, Output out) throws Exception {
                throw new IllegalStateException("业务异常");
            }
        });
        try {
            handler.handle("/", input, output);
            fail("下游异常应原样抛出");
        } catch (Exception e) {
            assertEquals("业务异常", e.getMessage());
        }
        assertNull("异常路径仍应清理上下文", LocaleContext.get());
    }

    @Test
    public void extractorExceptionSwallowed() throws Exception {
        TestableHandler handler = new TestableHandler(in -> {
            throw new RuntimeException("头读取失败");
        });
        handler.setNext(new Handler<Input, Output>() {
            @Override
            public void handle(String path, Input in, Output out) {
                assertNull("提取失败不绑定，落默认", LocaleContext.get());
            }
        });
        handler.handle("/", input, output);
    }

    @Test
    public void nullExtractorFallsBackToDefault() throws Exception {
        final String[] observed = new String[1];
        TestableHandler testable = new TestableHandler(null);
        testable.setNext(new Handler<Input, Output>() {
            @Override
            public void handle(String path, Input in, Output out) {
                observed[0] = LocaleContext.get();
            }
        });
        testable.handle("/", input, output);
        assertNull("无提取器不绑定", observed[0]);
    }

    /** 暴露 protected next 的测试子类。 */
    private static final class TestableHandler extends I18nHandler<Input, Output> {
        private TestableHandler(LocaleExtractor<Input> extractor) {
            super(extractor);
        }

        private void setNext(Handler<Input, Output> nextHandler) {
            this.next = nextHandler;
        }
    }
}
