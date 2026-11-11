/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import cn.aifei.log.Log;
import cn.aifei.log.LogFactory;
import cn.hg.aifei.trace.support.RecordingLog;

/**
 * TraceLogFactory 单元测试：三个 getLog 均返回包装 TraceLog、null delegate 拒绝（AC-10）。
 */
public class TraceLogFactoryTest {

    /**
     * 测试桩：每次 getLog 产出独立的 RecordingLog 并计数调用。
     */
    private static class FakeLogFactory implements LogFactory {

        int callCount;
        final RecordingLog nextLog = new RecordingLog();

        @Override
        public Log getLog() {
            callCount++;
            return nextLog;
        }

        @Override
        public Log getLog(Class<?> clazz) {
            callCount++;
            return nextLog;
        }

        @Override
        public Log getLog(String name) {
            callCount++;
            return nextLog;
        }
    }

    /**
     * 三个 getLog 变体均应返回 TraceLog 包装实例，且 getName 透传底层 delegate。
     */
    @Test
    public void allGetLogVariantsShouldReturnWrappedTraceLog() {
        FakeLogFactory fake = new FakeLogFactory();
        fake.nextLog.name = "wrapped-name";
        TraceLogFactory factory = new TraceLogFactory(fake);

        Log log1 = factory.getLog();
        Log log2 = factory.getLog(TraceLogFactoryTest.class);
        Log log3 = factory.getLog("explicit-name");
        assertEquals("三个 getLog 均应调用 delegate", 3, fake.callCount);

        assertTrue("getLog() 应返回 TraceLog", log1 instanceof TraceLog);
        assertTrue("getLog(Class) 应返回 TraceLog", log2 instanceof TraceLog);
        assertTrue("getLog(String) 应返回 TraceLog", log3 instanceof TraceLog);
        assertEquals("包装后 getName 应透传底层 delegate", "wrapped-name", log1.getName());
        assertEquals("wrapped-name", log2.getName());
        assertEquals("wrapped-name", log3.getName());
    }

    /**
     * 包装实例应真正工作：经 TraceLogFactory 产出的 Log 打印时拼装前缀（装配链端到端验证）。
     */
    @Test
    public void wrappedLogShouldCarryPrefix() {
        FakeLogFactory fake = new FakeLogFactory();
        TraceLogFactory factory = new TraceLogFactory(fake);
        TraceKit.init("0123456789abcdef0123456789abcdef");
        try {
            factory.getLog().info("hello");
        } finally {
            TraceKit.clear();
        }
        assertEquals("经工厂产出的 Log 打印应带前缀",
                java.util.Arrays.asList("[0123456789abcdef0123456789abcdef] hello"), fake.nextLog.messages);
    }

    /**
     * 场景 9：new TraceLogFactory(null) 拒绝，消息逐字对齐 §7.3。
     */
    @Test
    public void nullDelegateShouldReject() {
        try {
            new TraceLogFactory(null);
            fail("new TraceLogFactory(null) 应抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("delegate can not be null.", e.getMessage());
        }
    }

    /**
     * 构造后的 delegate 字段不可从外部重置：getLog 始终委托构造时给定的工厂（防自包装由显式入参保证）。
     */
    @Test
    public void delegateShouldBeFixedAtConstruction() {
        FakeLogFactory fake = new FakeLogFactory();
        TraceLogFactory factory = new TraceLogFactory(fake);
        assertNotNull(factory.getLog());
        assertSame("每次取用均委托同一构造时给定的工厂", fake, readDelegate(factory));
        assertEquals(1, fake.callCount);
    }

    /**
     * 反射读取 TraceLogFactory 的 delegate 字段（验证装饰器构造后绑定不变）。
     *
     * @param factory 被测工厂
     * @return 其内部 delegate
     */
    private static LogFactory readDelegate(TraceLogFactory factory) {
        try {
            java.lang.reflect.Field field = TraceLogFactory.class.getDeclaredField("delegate");
            field.setAccessible(true);
            return (LogFactory) field.get(factory);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法读取 delegate 字段", e);
        }
    }
}
