/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.aifei;

import cn.aifei.aop.Invocation;
import cn.hg.aifei.sentinel.aifei.SentinelInterceptor;
import cn.hg.aifei.sentinel.spi.SentinelBlockHandler;

/** AOP 路径实参晚于拦截器解析，热点端点从 HTTP 入参读取 id 供 Sentinel 按值统计。 */
public class DemoSentinelInterceptor extends SentinelInterceptor {

    private static final String DETAIL_RESOURCE = "demo.detail";
    private static final Object[] NO_ARGS = new Object[0];

    public DemoSentinelInterceptor(SentinelBlockHandler blockHandler) {
        super(blockHandler);
    }

    @Override
    protected Object[] argsOf(Invocation inv) {
        if (!DETAIL_RESOURCE.equals(resourceOf(inv))) {
            return NO_ARGS;
        }
        Integer id = inv.getInput() == null ? null : inv.getInput().getInt("id");
        return new Object[]{id == null ? 0 : id};
    }
}
