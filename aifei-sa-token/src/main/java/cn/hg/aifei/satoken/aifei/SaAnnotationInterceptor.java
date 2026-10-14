/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.hg.aifei.satoken.spi.SaTokenErrorHandler;
import cn.dev33.satoken.exception.StopMatchException;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;

/**
 * 按方法注解执行 Sa-Token 鉴权的 aifei 拦截器。
 *
 * <p>只处理 {@code @SaCheckLogin}、{@code @SaCheckRole}、{@code @SaCheckPermission}
 * 等注解带来的鉴权，不做路径匹配。</p>
 */
public class SaAnnotationInterceptor implements Interceptor {

    private final SaTokenErrorHandler errorHandler;

    /**
     * 创建注解鉴权拦截器。
     *
     * @param errorHandler 鉴权失败策略，不能为空
     * @throws IllegalArgumentException errorHandler 为空
     */
    public SaAnnotationInterceptor(SaTokenErrorHandler errorHandler) {
        if (errorHandler == null) {
            throw new IllegalArgumentException("errorHandler can not be null.");
        }
        this.errorHandler = errorHandler;
    }

    @Override
    public void intercept(Invocation inv) throws Exception {
        try {
            SaAnnotationStrategy.instance.checkMethodAnnotation.accept(inv.getMethod());
            inv.invoke();
        } catch (StopMatchException ignored) {
            // 方法上无注解或标注了 @SaIgnore：放行进入 action
            inv.invoke();
        } catch (Exception e) {
            if (!SaTokenErrorSupport.handle(inv, e, errorHandler)) {
                throw e;
            }
        }
    }
}
