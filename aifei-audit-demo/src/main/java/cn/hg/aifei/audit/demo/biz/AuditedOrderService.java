/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.biz;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.hg.aifei.audit.Audit;
import cn.hg.aifei.trace.TraceKit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 被审计的业务动作：本包整体由 {@code routes.scan(..., new AuditInterceptor())} 覆盖。
 *
 * <p>{@code /failure} 必抛业务异常，用来验证「失败事件照出 + 异常原样上抛」；
 * 由于框架会把异常收敛为服务端错误响应，页面另经 {@code /failure/evidence} 读取「同一实例」证据。</p>
 */
@Path("/audit/demo")
public class AuditedOrderService {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private static volatile IllegalStateException thrownByBiz;

    private static volatile IllegalStateException propagated;

    /**
     * 场景 1：被标注的业务动作，每次调用产出一条成功事件。
     *
     * @return 业务返回值
     */
    @Path("annotated")
    @Audit(action = "order.cancel", desc = "取消订单")
    public Out annotated() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("returnValue", "cancelled-" + SEQUENCE.incrementAndGet());
        data.put("traceId", TraceKit.getTraceId());
        return Out.ok().data(data);
    }

    /**
     * 场景 5：必抛业务异常，验证失败事件与异常原样上抛。
     *
     * @return 不会返回
     */
    @Path("failure")
    @Audit(action = "order.cancel", desc = "取消订单（业务异常）")
    public Out failure() {
        IllegalStateException error = new IllegalStateException("order-cancel-rejected");
        thrownByBiz = error;
        throw error;
    }

    /**
     * 场景 3 之一：同参数形态，开启入参记录并按参数位剔除第二个参数。
     *
     * @param keep   可见参数（索引 0）
     * @param secret 被剔除参数（索引 1）
     * @return 标记本次开启了入参记录
     */
    @Path("masked")
    @Audit(action = "order.masked", desc = "脱敏对照（记录入参）", args = true, mask = {1})
    public Out masked(@Para(name = "keep") String keep, @Para(name = "secret") String secret) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("recordArgs", true);
        data.put("returnValue", "ok");
        return Out.ok().data(data);
    }

    /**
     * 场景 3 之二：同一业务动作，注解不开启入参记录（注解属性编译期固定，故用独立动作表达两种配置）。
     *
     * @param keep   参数（不会被记录）
     * @param secret 参数（不会被记录）
     * @return 标记本次未开启入参记录
     */
    @Path("masked/off")
    @Audit(action = "order.masked", desc = "脱敏对照（不记录入参）")
    public Out maskedOff(@Para(name = "keep") String keep, @Para(name = "secret") String secret) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("recordArgs", false);
        data.put("returnValue", "ok");
        return Out.ok().data(data);
    }

    /**
     * @return 业务方法自己抛出的异常实例
     */
    public static IllegalStateException thrownByBiz() {
        return thrownByBiz;
    }

    /**
     * @return 经拦截器链冒泡出来的异常实例
     */
    public static IllegalStateException propagated() {
        return propagated;
    }

    /**
     * 观察型拦截器：记录冒泡出来的异常实例后原样上抛（用于证明「同一实例」）。
     */
    public static final class ThrownCaptureInterceptor implements Interceptor {

        @Override
        public void intercept(Invocation inv) throws Exception {
            try {
                inv.invoke();
            } catch (IllegalStateException e) {
                propagated = e;
                throw e;
            }
        }
    }
}
