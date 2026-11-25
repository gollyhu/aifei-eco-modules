/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.pv;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.core.Input;
import cn.aifei.argument.Argument;
import cn.aifei.core.Output;
import cn.aifei.router.Action;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * PV-02 前置实证：多拦截器时谁在外层，采集构件放最前能否捕获内层失败。
 *
 * <p>框架 {@code Invocation.invoke()} 的实现是「取 {@code interceptors[index++]} 调 {@code intercept(this)}」，
 * 每层拦截器自行调 {@code invoke()} 推进到下一层，故 <b>数组下标 0 即最外层</b>。本用例两面验证：</p>
 *
 * <pre>
 * ① 审计在最前（{@code [audit, biz]}）：内层抛异常时外层仍能记录失败事件；
 * ② 顺序反过来（{@code [biz, audit]}）：外层（biz）不推进调用链或先抛，审计根本不被执行 —— 反证顺序即语义。
 * </pre>
 */
public class Pv02InterceptorOrderProbeTest {

    /**
     * 内层业务方法：抛异常。
     */
    public static class ProbeService {

        public String boom() {
            throw new IllegalStateException("boom");
        }

        public String ok() {
            return "ok";
        }
    }

    /**
     * 链路记录器：记录进入 / 捕获 / 退出，可选在推进前就抛异常（模拟「内层先失败」）。
     */
    static final class StepInterceptor implements Interceptor {

        private final String name;

        private final List<String> steps;

        private final boolean throwBeforeInvoke;

        StepInterceptor(String name, List<String> steps, boolean throwBeforeInvoke) {
            this.name = name;
            this.steps = steps;
            this.throwBeforeInvoke = throwBeforeInvoke;
        }

        @Override
        public void intercept(Invocation inv) throws Exception {
            steps.add(name + ":enter");
            if (throwBeforeInvoke) {
                steps.add(name + ":veto");
                throw new IllegalArgumentException(name + "-veto");
            }
            try {
                inv.invoke();
            } catch (RuntimeException e) {
                steps.add(name + ":caught:" + e.getClass().getSimpleName());
                throw e;
            } finally {
                steps.add(name + ":exit");
            }
        }
    }

    /**
     * ① 审计在首位：内层拦截器（先抛异常、不推进）失败仍被外层感知，返回值亦被外层读取。
     */
    @Test
    public void firstRegisteredInterceptorIsOutermost() throws Exception {
        List<String> steps = new ArrayList<>();
        StepInterceptor audit = new StepInterceptor("audit", steps, false);
        StepInterceptor biz = new StepInterceptor("biz", steps, true);

        Invocation inv = invocation("ok", new Interceptor[]{audit, biz});
        try {
            inv.invoke();
            Assert.fail("内层异常应冒泡到调用方");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("biz-veto", expected.getMessage());
        }

        Assert.assertEquals("最外层先进入、内层后进入，异常按逆序冒泡", Arrays.asList(
                "audit:enter",
                "biz:enter",
                "biz:veto",
                "audit:caught:IllegalArgumentException",
                "audit:exit"), steps);
    }

    /**
     * ① 补充：业务方法自身抛异常时，外层同样能捕获。
     */
    @Test
    public void outermostSeesBusinessExceptionAndReturnValue() throws Exception {
        List<String> steps = new ArrayList<>();
        StepInterceptor audit = new StepInterceptor("audit", steps, false);

        Invocation boom = invocation("boom", new Interceptor[]{audit});
        try {
            boom.invoke();
            Assert.fail("业务异常应冒泡");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("boom", expected.getMessage());
        }
        Assert.assertTrue(steps.contains("audit:caught:IllegalStateException"));

        Invocation ok = invocation("ok", new Interceptor[]{audit});
        ok.invoke();
        Assert.assertEquals("外层可读到内层执行后的返回值", "ok", ok.getReturnValue());
    }

    /**
     * ② 反证：顺序颠倒时审计被挡在外层之外 —— 证明「审计必须首个注册」不是风格偏好而是语义要求。
     */
    @Test
    public void reversedOrderHidesAuditFromFailure() throws Exception {
        List<String> steps = new ArrayList<>();
        StepInterceptor biz = new StepInterceptor("biz", steps, true);
        StepInterceptor audit = new StepInterceptor("audit", steps, false);

        Invocation inv = invocation("ok", new Interceptor[]{biz, audit});
        try {
            inv.invoke();
            Assert.fail("biz 先抛，异常应冒泡");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("biz-veto", expected.getMessage());
        }

        Assert.assertFalse("顺序颠倒时审计拦截器根本不被执行", steps.contains("audit:enter"));
    }

    /**
     * 以 routes.scan 的形态装配 Invocation（单方法、无参）。
     *
     * @param methodName 方法名
     * @param interceptors 拦截器数组，下标 0 为最外层
     * @return 装配好的 Invocation
     * @throws NoSuchMethodException 方法不存在
     */
    private static Invocation invocation(String methodName, Interceptor[] interceptors)
            throws NoSuchMethodException {
        Method method = ProbeService.class.getMethod(methodName);
        Argument<?, ?, ?>[] arguments = new Argument<?, ?, ?>[0];
        Action action = new Action("targetPath", "/probe", interceptors, ProbeService.class, method, arguments);
        return new Invocation(action, new ProbeService(), (Input) null, (Output) null);
    }
}
