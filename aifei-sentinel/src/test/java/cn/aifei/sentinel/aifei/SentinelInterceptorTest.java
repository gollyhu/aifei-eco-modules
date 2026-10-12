/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.sentinel.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.proxy.Callback;
import cn.aifei.sentinel.spi.SentinelBlockHandler;
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
 * 覆盖放行透传与限流降级两条路径，不启动 HTTP。
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
