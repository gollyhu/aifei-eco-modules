/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.hg.aifei.sentinel.annotation.SentinelResource;
import cn.hg.aifei.sentinel.spi.SentinelBlockHandler;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sentinel 埋点拦截器：以方法注解的逻辑资源名为 entry，未标注时回退全限定类名#方法名，命中 BlockException 走降级。
 * 本类是 SDK 中唯一 import aifei 内核（cn.aifei.aop.*）的类。
 */
public class SentinelInterceptor implements Interceptor {

    /**
     * Method → 资源名缓存：注解为静态元数据、运行期不变，故无需失效。
     * 用 ClassValue 让缓存随类卸载一并回收，避免强引用 Method 阻塞 ClassLoader 释放。
     */
    static final ClassValue<ConcurrentHashMap<Method, String>> RESOURCE_CACHE =
            new ClassValue<ConcurrentHashMap<Method, String>>() {
                @Override
                protected ConcurrentHashMap<Method, String> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<Method, String>();
                }
            };

    private final SentinelBlockHandler blockHandler;

    public SentinelInterceptor(SentinelBlockHandler blockHandler) {
        this.blockHandler = blockHandler;
    }

    @Override
    public void intercept(Invocation inv) throws Exception {
        try (Entry entry = SphU.entry(resourceOf(inv));) {
            inv.invoke();
        } catch (BlockException e) {
            inv.setReturnValue(blockHandler.handle(e));
        }
    }

    /**
     * 资源名：方法标注 {@link SentinelResource} 且 value 非空白时取 value（trim 后），否则回退「全限定类名#方法名」。
     * 结果按 Method 缓存；子类覆盖本方法时注解与缓存均不生效。
     */
    protected String resourceOf(Invocation inv) {
        Method m = inv.getMethod();
        return RESOURCE_CACHE.get(m.getDeclaringClass()).computeIfAbsent(m, SentinelInterceptor::resolve);
    }

    private static String resolve(Method m) {
        SentinelResource anno = m.getAnnotation(SentinelResource.class);
        if (anno != null && anno.value() != null && !anno.value().trim().isEmpty()) {
            return anno.value().trim();
        }
        return m.getDeclaringClass().getName() + "#" + m.getName();
    }
}
