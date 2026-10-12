/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.sentinel.aifei;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.sentinel.spi.SentinelBlockHandler;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;

/**
 * Sentinel 埋点拦截器：以「全限定类名#方法名」为资源 entry，命中 BlockException 走降级。
 * 本类是 SDK 中唯一 import aifei 内核（cn.aifei.aop.*）的类。
 */
public class SentinelInterceptor implements Interceptor {

    private final SentinelBlockHandler blockHandler;

    public SentinelInterceptor(SentinelBlockHandler blockHandler) {
        this.blockHandler = blockHandler;
    }

    @Override
    public void intercept(Invocation inv) throws Exception {
        Entry entry = null;
        try {
            entry = SphU.entry(resourceOf(inv));
            inv.invoke();
        } catch (BlockException e) {
            inv.setReturnValue(blockHandler.handle(e));
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

    /** 资源名：默认「全限定类名#方法名」，规则按此维度配置；需按 path 维度时覆盖此方法 */
    protected String resourceOf(Invocation inv) {
        return inv.getMethod().getDeclaringClass().getName() + "#" + inv.getMethodName();
    }
}
