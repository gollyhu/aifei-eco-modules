/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import cn.aifei.aop.Invocation;
import cn.hg.aifei.audit.aifei.AuditInterceptor;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.support.AuditTestSupport;
import cn.hg.aifei.audit.support.CountingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 事件模型用例：AC-04 / AC-05 / AC-06 / AC-13（toString 部分）。
 */
public class AuditEventTest {

    private static cn.aifei.log.LogFactory previous;

    @BeforeClass
    public static void setUpClass() {
        previous = RecordingLogFactory.install();
    }

    @AfterClass
    public static void tearDownClass() {
        RecordingLogFactory.restore(previous);
    }

    /**
     * 被采集的业务对象。
     */
    public static class Service {

        @Audit(action = "order.cancel", desc = "取消订单")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }
    }

    @Test
    public void eventExposesAllFifteenFields() {
        AuditEvent event = new AuditEvent("id-1", 1700000000000L, "order.cancel", "demo", "取消订单",
                "alice", "Alice", "order-9", "#0=<masked>", AuditEvent.Result.FAILURE,
                "java.lang.IllegalStateException", "boom", 7L, "trace-1", "client-1");

        Assert.assertEquals("id-1", event.getEventId());
        Assert.assertEquals(1700000000000L, event.getOccurredAt());
        Assert.assertEquals("order.cancel", event.getAction());
        Assert.assertEquals("demo", event.getModule());
        Assert.assertEquals("取消订单", event.getDesc());
        Assert.assertEquals("alice", event.getActorId());
        Assert.assertEquals("Alice", event.getActorName());
        Assert.assertEquals("order-9", event.getTarget());
        Assert.assertEquals("#0=<masked>", event.getArgsSummary());
        Assert.assertTrue(event.getResult().isFailure());
        Assert.assertEquals("java.lang.IllegalStateException", event.getErrorCode());
        Assert.assertEquals("boom", event.getErrorMessage());
        Assert.assertEquals(7L, event.getElapsedMillis());
        Assert.assertEquals("trace-1", event.getTraceId());
        Assert.assertEquals("client-1", event.getClient());
    }

    @Test
    public void eventIsImmutable() throws Exception {
        AuditEvent event = new AuditEvent("id-2", 1L, "a", null, null, null, null, null, null,
                AuditEvent.Result.SUCCESS, null, null, 0L, null, null);

        Field[] fields = AuditEvent.class.getDeclaredFields();
        Assert.assertTrue("字段数应为 15", fields.length >= 15);
        for (int i = 0; i < fields.length; i++) {
            Assert.assertTrue("字段必须 final: " + fields[i].getName(), Modifier.isFinal(fields[i].getModifiers()));
        }

        Method[] methods = AuditEvent.class.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            String name = methods[i].getName();
            boolean setter = name.startsWith("set") && methods[i].getParameterTypes().length == 1;
            Assert.assertFalse("不得存在 setter: " + name, setter);
        }

        Field action = AuditEvent.class.getDeclaredField("action");
        boolean blocked;
        try {
            action.set(event, "tampered");
            blocked = false;
        } catch (IllegalAccessException e) {
            blocked = true;
        }
        Assert.assertTrue("反射置值应抛异常（无 setter、字段不可写）", blocked);
    }

    @Test
    public void eventIdsAreUniqueUnderConcurrentCollection() throws Exception {
        final CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        final int threads = 4;
        final int perThread = 25;
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        ready.countDown();
                        try {
                            go.await();
                            for (int j = 0; j < perThread; j++) {
                                Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                                        new Object[]{"o-" + j}, new AuditInterceptor());
                                inv.invoke();
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        } finally {
                            done.countDown();
                        }
                    }
                });
            }
            Assert.assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            Assert.assertTrue("采集应在 10 秒内完成", done.await(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            AuditHolder.clear();
        }

        List<AuditEvent> events = sink.events();
        Assert.assertEquals(threads * perThread, events.size());
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < events.size(); i++) {
            ids.add(events.get(i).getEventId());
        }
        Assert.assertEquals("100 次采集事件号去重后应恰为 100", threads * perThread, ids.size());
    }

    @Test
    public void toStringCarriesIdentifiersAndNoArgumentText() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));
        try {
            Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                    new Object[]{"SECRET-XYZ"}, new AuditInterceptor());
            inv.invoke();
            AuditEvent event = sink.first();
            Assert.assertNotNull(event);
            Assert.assertNull("默认不记录入参", event.getArgsSummary());
            String text = event.toString();
            Assert.assertTrue("toString 应含事件号", text.contains(event.getEventId()));
            Assert.assertTrue("toString 应含动作码", text.contains("order.cancel"));
            Assert.assertFalse("toString 不得出现入参原文", text.contains("SECRET-XYZ"));
        } finally {
            AuditHolder.clear();
        }
    }
}
