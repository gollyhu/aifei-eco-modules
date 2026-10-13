/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/** 路径鉴权决策与鉴权失败的响应转换 */
public class SaTokenInterceptorTest {

    private SaTokenContext previousContext;

    @Before
    public void setUp() {
        previousContext = SaManager.getSaTokenContext();
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
    }

    @After
    public void tearDown() {
        SaTokenContextForThreadLocalStaff.clearModelBox();
        SaManager.setSaTokenContext(previousContext);
    }

    @Test
    public void invokesActionWhenPathIsExcluded() throws Exception {
        bindPath("/auth/login");
        final boolean[] invoked = {false};

        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied")
                .setAnnotationCheck(false)
                .addExclude("/auth/login")
                .setAuth(() -> {
                    throw new SaTokenException("不应执行鉴权");
                });

        interceptor.intercept(invocation(() -> {
            invoked[0] = true;
            return "ok";
        }));

        Assert.assertTrue("排除路径应放行进入 action", invoked[0]);
    }

    @Test
    public void invokesActionWhenIncludeNotMatched() throws Exception {
        bindPath("/public/index");
        final boolean[] invoked = {false};

        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied")
                .setAnnotationCheck(false)
                .addInclude("/admin/**")
                .setAuth(() -> {
                    throw new SaTokenException("不应执行鉴权");
                });

        interceptor.intercept(invocation(() -> {
            invoked[0] = true;
            return "ok";
        }));

        Assert.assertTrue("未命中 include 应放行", invoked[0]);
    }

    @Test
    public void invokesActionWhenAuthPasses() throws Exception {
        bindPath("/admin/list");
        final boolean[] authed = {false};
        final boolean[] invoked = {false};

        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied")
                .setAnnotationCheck(false)
                .addInclude("/admin/**")
                .setAuth(() -> authed[0] = true);

        interceptor.intercept(invocation(() -> {
            invoked[0] = true;
            return "ok";
        }));

        Assert.assertTrue("命中 include 应执行鉴权", authed[0]);
        Assert.assertTrue("鉴权通过应进入 action", invoked[0]);
    }

    @Test
    public void convertsAuthFailureIntoHandlerResult() throws Exception {
        bindPath("/admin/list");
        final boolean[] invoked = {false};

        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied")
                .setAnnotationCheck(false)
                .addInclude("/admin/**")
                .setAuth(() -> {
                    throw new NotLoginException("未登录", "login", NotLoginException.NOT_TOKEN);
                });

        Invocation inv = invocation(() -> {
            invoked[0] = true;
            return "ok";
        });
        interceptor.intercept(inv);

        Assert.assertFalse("鉴权失败不应进入 action", invoked[0]);
        Assert.assertEquals("返回值应来自 errorHandler", "denied", inv.getReturnValue());
    }

    @Test
    public void returnsBackResultCarriedByException() throws Exception {
        bindPath("/admin/list");
        final boolean[] invoked = {false};

        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied")
                .setAnnotationCheck(false)
                .addInclude("/admin/**")
                .setAuth(() -> {
                    throw new BackResultException("direct-result");
                });

        Invocation inv = invocation(() -> {
            invoked[0] = true;
            return "ok";
        });
        interceptor.intercept(inv);

        Assert.assertFalse(invoked[0]);
        Assert.assertEquals("direct-result", inv.getReturnValue());
    }

    @Test
    public void propagatesNonSaTokenException() {
        bindPath("/admin/list");

        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied")
                .setAnnotationCheck(false)
                .addInclude("/admin/**");

        try {
            interceptor.intercept(invocation(() -> {
                throw new IllegalStateException("business-error");
            }));
            Assert.fail("非 Sa-Token 异常应继续向上传播");
        } catch (Exception expected) {
            Assert.assertEquals("business-error", expected.getMessage());
        }
    }

    @Test
    public void ruleListsAreUnmodifiable() {
        SaTokenInterceptor interceptor = new SaTokenInterceptor(e -> "denied").addInclude("/a/**");
        try {
            interceptor.getIncludeList().add("/b/**");
            Assert.fail("规则列表应为只读视图");
        } catch (UnsupportedOperationException expected) {
            // 预期
        }
        Assert.assertTrue(interceptor.getExcludeList().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullErrorHandler() {
        new SaTokenInterceptor(null);
    }

    private static void bindPath(String path) {
        SaTokenContextForThreadLocalStaff.setModelBox(new StubRequest(path), null, null);
    }

    /** 构造一个只含 action 回调的 Invocation，action 返回值即回调结果 */
    private static Invocation invocation(Supplier<Object> action) throws NoSuchMethodException {
        Method method = Target.class.getMethod("index");
        return new Invocation(new Target(), method, new Object[0], new Interceptor[0], args -> action.get());
    }

    /** 仅提供路径的请求替身，其余能力在本用例中不被访问 */
    private static final class StubRequest implements SaRequest {

        private final String path;

        StubRequest(String path) {
            this.path = path;
        }

        @Override
        public Object getSource() {
            return null;
        }

        @Override
        public String getParam(String name) {
            return null;
        }

        @Override
        public Collection<String> getParamNames() {
            return Collections.<String>emptyList();
        }

        @Override
        public Map<String, String> getParamMap() {
            return Collections.<String, String>emptyMap();
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public String getCookieValue(String name) {
            return null;
        }

        @Override
        public String getCookieFirstValue(String name) {
            return null;
        }

        @Override
        public String getCookieLastValue(String name) {
            return null;
        }

        @Override
        public String getRequestPath() {
            return path;
        }

        @Override
        public String getUrl() {
            return null;
        }

        @Override
        public String getMethod() {
            return "GET";
        }

        @Override
        public String getHost() {
            return null;
        }

        @Override
        public Object forward(String path) {
            return null;
        }
    }

    /** action 目标替身，仅用于提供 Method 句柄 */
    public static final class Target {

        public String index() {
            return "ok";
        }
    }
}
