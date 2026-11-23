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
