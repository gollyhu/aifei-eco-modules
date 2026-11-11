/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;

/**
 * TraceContext 单元测试：null = 移除语义、clear 幂等、两个 ThreadLocal 独立存取。
 */
public class TraceContextTest {

    @After
    public void cleanUp() {
        TraceContext.clear();
    }

    /**
     * set(null) 等价于移除：绑定后传 null，get 返回 null。
     */
    @Test
    public void setNullShouldRemove() {
        TraceContext.setTraceId("t1");
        TraceContext.setSpanId("s1");
        assertEquals("t1", TraceContext.getTraceId());
        assertEquals("s1", TraceContext.getSpanId());

        TraceContext.setTraceId(null);
        TraceContext.setSpanId(null);
        assertNull("setTraceId(null) 应等价移除", TraceContext.getTraceId());
        assertNull("setSpanId(null) 应等价移除", TraceContext.getSpanId());
    }

    /**
     * clear() 幂等：重复调用与对未绑定线程调用均安全。
     */
    @Test
    public void clearShouldBeIdempotent() {
        TraceContext.clear();
        assertNull(TraceContext.getTraceId());
        TraceContext.clear();
        TraceContext.clear();
        assertNull(TraceContext.getTraceId());
        assertNull(TraceContext.getSpanId());
    }

    /**
     * TraceId 与 SpanId 两个 ThreadLocal 独立存取，互不影响。
     */
    @Test
    public void traceIdAndSpanIdShouldBeIndependent() {
        TraceContext.setTraceId("t1");
        assertNull("绑定 traceId 不得影响 spanId", TraceContext.getSpanId());

        TraceContext.setSpanId("s1");
        assertEquals("t1", TraceContext.getTraceId());
        assertEquals("s1", TraceContext.getSpanId());

        TraceContext.setTraceId(null);
        assertEquals("移除 traceId 不得影响 spanId", "s1", TraceContext.getSpanId());

        TraceContext.clear();
        assertNull(TraceContext.getSpanId());
    }

    /**
     * get 未绑定时返回 null。
     */
    @Test
    public void gettersShouldReturnNullWhenUnbound() {
        assertNull(TraceContext.getTraceId());
        assertNull(TraceContext.getSpanId());
    }
}
