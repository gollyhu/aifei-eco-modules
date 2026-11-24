/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.proxy.Callback;
import cn.hg.aifei.sentinel.annotation.SentinelResource;
import cn.hg.aifei.sentinel.spi.SentinelBlockHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * 行为测试：以真实 Invocation（target, method, args, interceptors, callback 构造器）+ 真实 Sentinel 驱动，
 * 覆盖放行透传、限流降级与注解资源名命中 / 回退，不启动 HTTP。
 */
public class SentinelInterceptorTest {

    private static final String BLOCKED = "blocked";
    private static final SentinelBlockHandler BLOCK_HANDLER = e -> BLOCKED;
    private static final SentinelInterceptor INTERCEPTOR = new SentinelInterceptor(BLOCK_HANDLER);

    public static class DemoService {
        int indexCalls;
        int blockedCalls;

        public String index() {
            indexCalls++;
            return "ok";
        }

        public String blocked() {
            blockedCalls++;
            return "ok";
        }
    }

    public static class AnnotatedService {
        int logicCalls;
        int blankCalls;

        @SentinelResource("unit.logic")
        public String logic() {
            logicCalls++;
            return "ok";
        }

        @SentinelResource("   ")
        public String blank() {
            blankCalls++;
            return "ok";
        }
    }

    public static class ParamService {
        int calls;

        @SentinelResource("unit.param.hot")
        public String detail(int id) {
            calls++;
            return "ok-" + id;
        }
    }

    @Test
    public void testPassThroughWhenNoRule() throws Exception {
        DemoService svc = new DemoService();
        Object ret = invoke(svc, "index");
        Assert.assertEquals("ok", ret);
        Assert.assertEquals(1, svc.indexCalls);
    }

    @Test
    public void testBlockedWhenFlowRuleHit() throws Exception {
        // QPS=0：任何请求都被拒绝
        FlowRule rule = new FlowRule(DemoService.class.getName() + "#blocked");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(Collections.singletonList(rule));

        DemoService svc = new DemoService();
        Object ret = invoke(svc, "blocked");
        Assert.assertEquals(BLOCKED, ret);       // 降级返回值
        Assert.assertEquals(0, svc.blockedCalls); // 业务未执行
    }

    @Test
    public void testAnnotatedResourceNameIsUsed() throws Exception {
        FlowRule rule = new FlowRule("unit.logic");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(Collections.singletonList(rule));

        AnnotatedService svc = new AnnotatedService();
        Object ret = invoke(svc, "logic");
        Assert.assertEquals(BLOCKED, ret);
        Assert.assertEquals(0, svc.logicCalls);
    }

    @Test
    public void testBlankAnnotationFallsBackToDefault() throws Exception {
        FlowRule rule = new FlowRule(AnnotatedService.class.getName() + "#blank");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(Collections.singletonList(rule));

        AnnotatedService svc = new AnnotatedService();
        Object ret = invoke(svc, "blank");
        Assert.assertEquals(BLOCKED, ret);
        Assert.assertEquals(0, svc.blankCalls);
    }

    @Test
    public void testAnnotatedResourceNameCached() throws Exception {
        Method m = AnnotatedService.class.getMethod("logic");
        Invocation inv = invocationOf(new AnnotatedService(), m);

        Assert.assertEquals("unit.logic", INTERCEPTOR.resourceOf(inv));
        String cached = INTERCEPTOR.resourceOf(inv);
        Assert.assertSame(cached, INTERCEPTOR.resourceOf(inv));
        Assert.assertTrue(SentinelInterceptor.RESOURCE_CACHE.get(AnnotatedService.class).containsKey(m));
    }

    @Test
    public void testDefaultResourceNameCached() throws Exception {
        Method m = DemoService.class.getMethod("index");
        String expected = DemoService.class.getName() + "#index";
        Invocation inv = invocationOf(new DemoService(), m);

        Assert.assertEquals(expected, INTERCEPTOR.resourceOf(inv));
        String cached = INTERCEPTOR.resourceOf(inv);
        Assert.assertSame(cached, INTERCEPTOR.resourceOf(inv));
        Assert.assertTrue(SentinelInterceptor.RESOURCE_CACHE.get(DemoService.class).containsKey(m));
    }

    @Test
    public void testConcurrentResourceNameConsistent() throws Exception {
        Method m = AnnotatedService.class.getMethod("logic");
        final Invocation inv = invocationOf(new AnnotatedService(), m);
        final String[] results = new String[8];
        Thread[] threads = new Thread[results.length];
        for (int i = 0; i < threads.length; i++) {
            final int idx = i;
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    results[idx] = INTERCEPTOR.resourceOf(inv);
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        for (String r : results) {
            Assert.assertEquals("unit.logic", r);
        }
    }

    @Test
    public void testParamFlowHitWithMethodArgs() throws Exception {
        paramRule();
        ParamService svc = new ParamService();
        Method m = ParamService.class.getMethod("detail", int.class);
        Callback callback = args -> {
            svc.calls++;
            return "ok";
        };

        Assert.assertEquals(BLOCKED, invokeWithArgs(new SentinelInterceptor(BLOCK_HANDLER), svc, m,
                new Object[]{7}, callback));
        Assert.assertEquals(0, svc.calls);
    }

    @Test
    public void testParamFlowHitWithOverriddenArgs() throws Exception {
        paramRule();
        ParamService svc = new ParamService();
        Method m = ParamService.class.getMethod("detail", int.class);
        SentinelInterceptor interceptor = new SentinelInterceptor(BLOCK_HANDLER) {
            @Override
            protected Object[] argsOf(Invocation inv) {
                return new Object[]{7};
            }
        };
        Callback callback = args -> {
            svc.calls++;
            return "ok";
        };

        // AOP 路径构造期 args 为空，由覆盖的 argsOf 提供实参后热点规则命中
        Assert.assertEquals(BLOCKED, invokeWithArgs(interceptor, svc, m, new Object[]{}, callback));
        Assert.assertEquals(0, svc.calls);
    }

    @Test
    public void testParamFlowPassWhenArgsMissing() throws Exception {
        paramRule();
        ParamService svc = new ParamService();
        Method m = ParamService.class.getMethod("detail", int.class);
        Callback callback = args -> {
            svc.calls++;
            return "ok";
        };

        Assert.assertEquals("ok", invokeWithArgs(new SentinelInterceptor(BLOCK_HANDLER), svc, m,
                new Object[]{}, callback));
        Assert.assertEquals(1, svc.calls);
    }

    private static void paramRule() {
        ParamFlowRule rule = new ParamFlowRule("unit.param.hot");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        rule.setParamIdx(0);
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }

    private static Object invokeWithArgs(SentinelInterceptor interceptor, Object target, Method m,
                                         Object[] args, Callback callback) throws Exception {
        Invocation inv = new Invocation(target, m, args, new Interceptor[]{interceptor}, callback);
        inv.invoke();
        return inv.getReturnValue();
    }

    private static Invocation invocationOf(Object target, Method m) {
        Callback callback = args -> null;
        return new Invocation(target, m, new Object[]{}, new Interceptor[]{}, callback);
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method m = target.getClass().getMethod(methodName);
        Callback callback = args -> {
            try {
                return m.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable t = e.getTargetException();
                throw t instanceof Exception ? (Exception) t : new RuntimeException(t);
            }
        };
        Invocation inv = new Invocation(target, m, new Object[]{}, new Interceptor[]{INTERCEPTOR}, callback);
        inv.invoke();
        return inv.getReturnValue();
    }
}
