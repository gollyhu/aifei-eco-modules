/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.hg.aifei.audit.internal.AuditCollector;
import cn.hg.aifei.audit.internal.AuditInvocation;
import java.lang.reflect.Method;

/**
 * 采集拦截器（接入构件，非承诺 API）：薄适配器，把框架调用现场包成 {@link AuditInvocation} 端口。
 *
 * <p>必须第一个传入 {@code routes.scan(...)}：拦截器数组下标 0 即最外层，顺序颠倒时审计完全失效。
 * 因无状态，{@code start()} / {@code stop()} 立即对已注册实例生效，无需重新扫描路由。</p>
 */
public class AuditInterceptor implements Interceptor {

    public AuditInterceptor() {
    }

    @Override
    public void intercept(final Invocation inv) throws Exception {
        AuditCollector.intercept(new AuditInvocation() {
            @Override
            public Method method() {
                return inv.getMethod();
            }

            @Override
            public void proceed() throws Exception {
                inv.invoke();
            }

            @Override
            public Object[] args() {
                return inv.getArgs();
            }

            @Override
            public Object returnValue() {
                return inv.getReturnValue();
            }
        });
    }
}
