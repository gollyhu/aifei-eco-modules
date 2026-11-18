/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.pv;

import cn.aifei.argument.Argument;
import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.core.Input;
import cn.aifei.core.Output;
import cn.aifei.proxy.Callback;
import cn.aifei.router.Action;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;

/**
 * PV-01 前置实证：拦截器能否读到被拦截方法的实际入参。
 *
 * <p>两条构造路径分别验证，结论不同，二者不可互相替代：</p>
 *
 * <pre>
 * ① Invocation(target, method, args, interceptors, callback) —— ProxyFactory 路径，args 构造期即就位；
 * ② Invocation(action, target, input, output) —— routes.scan AOP 路径（本模块实际使用的路径），
 *    args 在 invoke() 内、拦截器全部执行完毕后才赋值，故拦截器「进入时」读到 null。
 * </pre>
 *
 * <p>{@link Action} 的公开构造器由框架直接暴露，故可用匿名 {@link Argument} 造出与真实路由一致的
 * Invocation，无需启动 HTTP 服务。</p>
 */
public class Pv01ArgsProbeTest {

    /**
     * 被拦截的业务对象：一个正常方法 + 一个必抛异常方法。
     */
    public static class ProbeService {

        public String action(String a, String b) {
            return a + "|" + b;
        }

        public String boom(String a) {
            throw new IllegalStateException("boom:" + a);
        }
    }

    /**
     * 拦截器内的入参快照：进入业务前后各读一次。
     */
    static final class ArgSnapshotInterceptor implements Interceptor {

        Object[] beforeInvoke;

        Object[] afterInvoke;

        Exception beforeGetArg0Failure;

        Object afterInvokeArg0;

        Throwable businessFailure;

        @Override
        public void intercept(Invocation inv) throws Exception {
            beforeInvoke = inv.getArgs();
            try {
                inv.getArg(0);
            } catch (Exception e) {
                beforeGetArg0Failure = e;
            }
            try {
                inv.invoke();
            } catch (RuntimeException e) {
                businessFailure = e;
            } finally {
                afterInvoke = inv.getArgs();
                try {
                    afterInvokeArg0 = inv.getArg(0);
                } catch (RuntimeException e) {
                    afterInvokeArg0 = e;
                }
            }
        }
    }

    /**
     * 固定值实参：与框架 Argument 同形（getValue 无视 input / output）。
     */
    private static Argument<Input, Output, String> fixed(String value) {
        return new Argument<Input, Output, String>() {
            @Override
            public String getValue(Input input, Output output) {
                return value;
            }
        };
    }

    /**
     * ① Proxy 路径：构造期传入的 args 在拦截器内可读。
     */
    @Test
    public void proxyPathArgsReadableBeforeInvoke() throws Exception {
        ProbeService target = new ProbeService();
        Method method = ProbeService.class.getMethod("action", String.class, String.class);
        ArgSnapshotInterceptor probe = new ArgSnapshotInterceptor();
        Callback callback = args -> method.invoke(target, args);
        Invocation inv = new Invocation(target, method, new Object[]{"a1", "b2"},
                new Interceptor[]{probe}, callback);

        inv.invoke();

        Assert.assertEquals("Proxy 路径：进入业务前即可读到 args",
                Arrays.asList("a1", "b2"), Arrays.asList(probe.beforeInvoke));
        Assert.assertEquals("Proxy 路径：进入业务前 getArg(0) 可用", "a1", probe.afterInvokeArg0);
        Assert.assertEquals("a1|b2", inv.getReturnValue());
    }

    /**
     * ② routes.scan AOP 路径（正常返回）：进入业务前 args 为 null，业务执行后可读。
     */
    @Test
    public void aopPathArgsNullBeforeInvokeAndReadableAfter() throws Exception {
        ArgSnapshotInterceptor probe = new ArgSnapshotInterceptor();
        Invocation inv = aopInvocation("action", probe, new String[]{"a1", "b2"});

        inv.invoke();

        Assert.assertNull("AOP 路径：拦截器进入时 args 尚未赋值（框架在拦截器链之后才解析实参）",
                probe.beforeInvoke);
        Assert.assertNotNull("AOP 路径：拦截器进入时 getArg(0) 抛异常", probe.beforeGetArg0Failure);
        Assert.assertEquals("NullPointerException", probe.beforeGetArg0Failure.getClass().getSimpleName());
        Assert.assertEquals("AOP 路径：invoke() 之后 args 可读",
                Arrays.asList("a1", "b2"), Arrays.asList(probe.afterInvoke));
        Assert.assertEquals("AOP 路径：invoke() 之后 getArg(0) 可读", "a1", probe.afterInvokeArg0);
        Assert.assertEquals("a1|b2", inv.getReturnValue());
    }

    /**
     * ② routes.scan AOP 路径（业务抛异常）：异常路径下 args 同样可读，异常原样冒泡。
     */
    @Test
    public void aopPathArgsReadableWhenBusinessThrows() throws Exception {
        ArgSnapshotInterceptor probe = new ArgSnapshotInterceptor();
        Invocation inv = aopInvocation("boom", probe, new String[]{"a1"});

        inv.invoke();

        Assert.assertTrue("业务异常由 invoke() 原样抛出", probe.businessFailure instanceof IllegalStateException);
        Assert.assertNull("异常路径下拦截器进入时 args 同样为 null", probe.beforeInvoke);
        Assert.assertEquals("异常路径下 args 仍可读", Arrays.asList("a1"), Arrays.asList(probe.afterInvoke));
    }

    /**
     * 按 routes.scan 的真实形态构造 Invocation：Action 持有 interceptor 数组与实参解析器。
     *
     * @param methodName 被调用的方法名（无参 / 单参 / 双参 String 形态）
     * @param probe      探针拦截器
     * @param values     实参值
     * @return 已装配好的 Invocation
     * @throws NoSuchMethodException 方法不存在
     */
    private static Invocation aopInvocation(String methodName, Interceptor probe, String[] values)
            throws NoSuchMethodException {
        Class<?>[] types = new Class<?>[values.length];
        Argument<?, ?, ?>[] arguments = new Argument<?, ?, ?>[values.length];
        for (int i = 0; i < values.length; i++) {
            types[i] = String.class;
            arguments[i] = fixed(values[i]);
        }
        Method method = ProbeService.class.getMethod(methodName, types);
        Action action = new Action("targetPath", "/probe",
                new Interceptor[]{probe}, ProbeService.class, method, arguments);
        return new Invocation(action, new ProbeService(), null, null);
    }
}
