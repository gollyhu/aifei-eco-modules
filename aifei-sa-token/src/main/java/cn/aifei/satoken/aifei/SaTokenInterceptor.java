/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.satoken.internal.PathPatternMatcher;
import cn.aifei.satoken.spi.SaAuthStrategy;
import cn.aifei.satoken.spi.SaTokenErrorHandler;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.StopMatchException;
import cn.dev33.satoken.strategy.SaAnnotationStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 按路径规则执行 Sa-Token 鉴权的 aifei 拦截器。
 *
 * <p>典型装配：</p>
 * <pre>
 * routes.scan("cn.demo.service", new SaTokenInterceptor(SaTokenUndertowError.defaultHandler())
 *         .addInclude("/**")
 *         .addExclude("/auth/login")
 *         .setAuth(() -&gt; StpUtil.checkLogin()));
 * </pre>
 */
public class SaTokenInterceptor implements Interceptor {

    private final List<String> includeList = new ArrayList<>();
    private final List<String> excludeList = new ArrayList<>();
    private final SaTokenErrorHandler errorHandler;

    private SaAuthStrategy auth = () -> {};

    private boolean annotationCheck = true;

    /**
     * 创建路径鉴权拦截器。
     *
     * @param errorHandler 鉴权失败策略，不能为空
     * @throws IllegalArgumentException errorHandler 为空
     */
    public SaTokenInterceptor(SaTokenErrorHandler errorHandler) {
        if (errorHandler == null) {
            throw new IllegalArgumentException("errorHandler can not be null.");
        }
        this.errorHandler = errorHandler;
    }

    @Override
    public void intercept(Invocation inv) throws Exception {
        try {
            if (matched() && checkAnnotation(inv)) {
                auth.check();
            }
            inv.invoke();
        } catch (StopMatchException ignored) {
            // 注解要求跳过鉴权，或鉴权链主动停止匹配：放行进入 action
            inv.invoke();
        } catch (Exception e) {
            if (!SaTokenErrorSupport.handle(inv, e, errorHandler)) {
                throw e;
            }
        }
    }

    /**
     * 追加纳入鉴权的路径规则。
     *
     * @param paths Ant 风格路径表达式
     * @return 当前拦截器实例
     */
    public SaTokenInterceptor addInclude(String... paths) {
        includeList.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * 追加排除鉴权的路径规则。
     *
     * @param paths Ant 风格路径表达式
     * @return 当前拦截器实例
     */
    public SaTokenInterceptor addExclude(String... paths) {
        excludeList.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * 设置路径命中后执行的鉴权动作。
     *
     * @param auth 鉴权策略；默认什么都不做
     * @return 当前拦截器实例
     */
    public SaTokenInterceptor setAuth(SaAuthStrategy auth) {
        if (auth != null) {
            this.auth = auth;
        }
        return this;
    }

    /**
     * 设置是否在路径命中后继续执行方法注解校验。
     *
     * @param annotationCheck false 时只按路径鉴权，注解校验交给独立的注解拦截器
     * @return 当前拦截器实例
     */
    public SaTokenInterceptor setAnnotationCheck(boolean annotationCheck) {
        this.annotationCheck = annotationCheck;
        return this;
    }

    /**
     * @return 纳入鉴权的路径规则（只读视图）
     */
    public List<String> getIncludeList() {
        return Collections.unmodifiableList(includeList);
    }

    /**
     * @return 排除鉴权的路径规则（只读视图）
     */
    public List<String> getExcludeList() {
        return Collections.unmodifiableList(excludeList);
    }

    /** 当前请求路径是否命中 include 且未命中 exclude */
    private boolean matched() {
        String path = SaHolder.getRequest().getRequestPath();
        if (path == null) {
            return false;
        }
        return (includeList.isEmpty() || anyMatch(includeList, path)) && !anyMatch(excludeList, path);
    }

    /** 校验方法注解；返回 false 表示注解层要求跳过后续鉴权 */
    private boolean checkAnnotation(Invocation inv) {
        if (!annotationCheck) {
            return true;
        }
        try {
            SaAnnotationStrategy.instance.checkMethodAnnotation.accept(inv.getMethod());
            return true;
        } catch (StopMatchException ignored) {
            return false;
        }
    }

    private static boolean anyMatch(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (PathPatternMatcher.get(pattern).matches(path)) {
                return true;
            }
        }
        return false;
    }
}
