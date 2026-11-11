/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Test;

/**
 * TraceKit 单元测试：覆盖设计 §7.2 场景 1–3 / 7 / 11–13（生成器替换用例在 @After 恢复默认）。
 */
public class TraceKitTest {

    @After
    public void restoreDefaults() {
        // 测试间隔离：恢复默认生成器并清理当前线程上下文
        TraceKit.setTraceIdGenerator(DefaultTraceIdGenerator.INSTANCE);
        TraceKit.clear();
    }

    /**
     * 场景 1：setTraceIdGenerator(null) 拒绝，消息逐字对齐 §7.3。
     */
    @Test
    public void setNullGeneratorShouldReject() {
        try {
            TraceKit.setTraceIdGenerator(null);
            fail("setTraceIdGenerator(null) 应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("generator can not be null.", e.getMessage());
        }
    }

    /**
     * 场景 2：init(null) / init("") / init("   ") 回退生成新 TraceId 并绑定。
     */
    @Test
    public void initNullOrBlankShouldFallbackToGenerate() {
        for (String blank : new String[] {null, "", "   "}) {
            String traceId = TraceKit.init(blank);
            assertNotNull("空白入参应回退生成非空 TraceId", traceId);
            assertEquals("回退生成的 TraceId 应已绑定", traceId, TraceKit.getTraceId());
            assertEquals(32, traceId.length());
        }
    }

    /**
     * 场景 3：init(" abc ") trim 后绑定 "abc"。
     */
    @Test
    public void initShouldTrimTraceId() {
        assertEquals("abc", TraceKit.init(" abc "));
        assertEquals("abc", TraceKit.getTraceId());
    }

    /**
     * 场景 7：getTraceId() / getSpanId() 未绑定时返回 null。
     */
    @Test
    public void gettersShouldReturnNullWhenUnbound() {
        assertNull(TraceKit.getTraceId());
        assertNull(TraceKit.getSpanId());
    }

    /**
     * 场景 11：TraceKit 未做任何配置直接调用即正常工作（生成器有默认值）。
     */
    @Test
    public void shouldWorkWithoutAnyConfiguration() {
        assertSame(DefaultTraceIdGenerator.INSTANCE, TraceKit.getTraceIdGenerator());
        String traceId = TraceKit.init();
        assertNotNull(traceId);
        assertEquals(traceId, TraceKit.getTraceId());
        String spanId = TraceKit.startSpan();
        assertNotNull(spanId);
        assertEquals(spanId, TraceKit.getSpanId());
    }

    /**
     * 场景 13：重复 clear() 与对未绑定线程 clear() 幂等安全。
     */
    @Test
    public void clearShouldBeIdempotent() {
        TraceKit.clear();
        assertNull(TraceKit.getTraceId());
        TraceKit.clear();
        TraceKit.clear();
        assertNull(TraceKit.getTraceId());
        assertNull(TraceKit.getSpanId());
    }

    /**
     * startSpan() 覆盖旧 spanId 且不影响 traceId（单层覆盖语义）。
     */
    @Test
    public void startSpanShouldOverrideSpanIdWithoutTouchingTraceId() {
        String traceId = TraceKit.init();
        String span1 = TraceKit.startSpan();
        String span2 = TraceKit.startSpan();
        assertNotNull(span1);
        assertNotNull(span2);
        assertNotEquals("再次 startSpan 应覆盖旧 spanId", span1, span2);
        assertEquals("spanId 覆盖不得影响 traceId", traceId, TraceKit.getTraceId());
        assertEquals(span2, TraceKit.getSpanId());
    }

    /**
     * 替换生成器后 init() 走新生成器；getTraceIdGenerator() 返回当前值；@After 恢复默认。
     */
    @Test
    public void setGeneratorShouldTakeEffect() {
        final String fakeId = "0123456789abcdef0123456789abcdef";
        TraceIdGenerator custom = new TraceIdGenerator() {
            @Override
            public String generate() {
                return fakeId;
            }
        };
        TraceKit.setTraceIdGenerator(custom);
        assertSame("getTraceIdGenerator 应返回当前生成器", custom, TraceKit.getTraceIdGenerator());
        assertEquals("替换后 init() 应走新生成器", fakeId, TraceKit.init());
        assertEquals(fakeId, TraceKit.getTraceId());

        TraceKit.setTraceIdGenerator(DefaultTraceIdGenerator.INSTANCE);
        assertSame(DefaultTraceIdGenerator.INSTANCE, TraceKit.getTraceIdGenerator());
        assertNotEquals("恢复默认后不再产出固定值", fakeId, TraceKit.init());
    }

    /**
     * init 返回值恒为最终生效值：非空白入参返回 trim 后原值，空白入参返回生成值。
     */
    @Test
    public void initShouldReturnEffectiveTraceId() {
        assertEquals("abc", TraceKit.init("abc"));
        String generated = TraceKit.init();
        assertNotNull(generated);
        assertEquals(generated, TraceKit.getTraceId());
    }
}
