/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import java.lang.reflect.Method;

/**
 * 调用现场端口（非承诺 API）：接入层把框架调用现场适配成本接口，{@code internal} 只认本接口。
 *
 * <p>{@link #args()} 只有在 {@link #proceed()} 走完拦截器链后才就绪，故采集落在 {@code finally}。</p>
 */
public interface AuditInvocation {

    /**
     * @return 被调用的业务方法
     */
    Method method();

    /**
     * 推进拦截器链。
     *
     * @throws Exception 业务方法原样抛出的异常
     */
    void proceed() throws Exception;

    /**
     * @return 实参，{@link #proceed()} 之后才就绪
     */
    Object[] args();

    /**
     * @return 业务返回值，{@link #proceed()} 之后才就绪
     */
    Object returnValue();
}
