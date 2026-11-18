/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import cn.aifei.aop.Interceptor;
import cn.aifei.aop.Invocation;
import cn.aifei.log.LogFactory;
import cn.hg.aifei.audit.aifei.AuditInterceptor;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.support.AuditTestSupport;
import cn.hg.aifei.audit.support.CountingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import cn.hg.aifei.audit.support.StubActorProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 注解路径用例：AC-01 / AC-02 / AC-03 / AC-21。
 */
public class AnnotationCollectTest {

    private static LogFactory previous;

    @BeforeClass
    public static void setUpClass() {
        previous = RecordingLogFactory.install();
    }

    @AfterClass
    public static void tearDownClass() {
        RecordingLogFactory.restore(previous);
    }

    @After
    public void tearDown() {
        AuditHolder.clear();
    }

    /**
     * 被采集的业务对象：标注方法与未标注方法并存。
     */
    public static class AnnotatedService {

        @Audit(action = "order.cancel", desc = "取消订单")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }

        public String plain(String orderId) {
            return "plain-" + orderId;
        }

        @Audit(action = "order.boom", desc = "取消失败")
        public String boom(String orderId) {
            throw new IllegalStateException("boom:" + orderId);
        }
    }

    /**
     * 内层拦截器：推进业务前直接否决。
     */
    public static class VetoInterceptor implements Interceptor {

        @Override
        public void intercept(Invocation inv) throws Exception {
            throw new IllegalStateException("veto");
        }
    }

    @Test
    public void unannotatedMethodProducesNoEventWhileAnnotatedProducesOne() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation plain = AuditTestSupport.invocation(AnnotatedService.class, "plain",
                new Object[]{"o-1"}, new AuditInterceptor());
        plain.invoke();
        Assert.assertEquals("未标注方法不得产事件", 0, sink.count());
        Assert.assertEquals("plain-o-1", plain.getReturnValue());

        Invocation annotated = AuditTestSupport.invocation(AnnotatedService.class, "cancel",
                new Object[]{"o-1"}, new AuditInterceptor());
        annotated.invoke();
        Assert.assertEquals("标注方法每次调用恰 1 条", 1, sink.count());
    }

    @Test
    public void successEventCarriesActorAndNoError() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, StubActorProvider.of("alice", "Alice"),
                null, null, null, "audit.module", "demo-module"));

        Invocation inv = AuditTestSupport.invocation(AnnotatedService.class, "cancel",
                new Object[]{"o-2"}, new AuditInterceptor());
        inv.invoke();

        AuditEvent event = sink.first();
        Assert.assertNotNull(event);
        Assert.assertEquals(1, sink.count());
        Assert.assertFalse(event.getResult().isFailure());
        Assert.assertEquals("alice", event.getActorId());
        Assert.assertEquals("Alice", event.getActorName());
        Assert.assertEquals("demo-module", event.getModule());
        Assert.assertEquals("取消订单", event.getDesc());
        Assert.assertTrue("耗时不得为负", event.getElapsedMillis() >= 0L);
        Assert.assertNull(event.getErrorCode());
        Assert.assertNull(event.getErrorMessage());
        Assert.assertTrue("发生时刻应大于 0", event.getOccurredAt() > 0L);
        Assert.assertFalse("事件号不得为空", event.getEventId().isEmpty());
    }

    @Test
    public void failureEventIsRecordedAndBusinessExceptionIsRethrown() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(AnnotatedService.class, "boom",
                new Object[]{"o-3"}, new AuditInterceptor());
        IllegalStateException caught = null;
        try {
            inv.invoke();
        } catch (IllegalStateException e) {
            caught = e;
        }

        Assert.assertNotNull("业务异常必须原样上抛", caught);
        Assert.assertEquals("boom:o-3", caught.getMessage());
        Assert.assertEquals(1, sink.count());
        AuditEvent event = sink.first();
        Assert.assertTrue(event.getResult().isFailure());
        Assert.assertTrue("错误码应含异常类全名: " + event.getErrorCode(),
                event.getErrorCode().contains("IllegalStateException"));
        Assert.assertTrue("错误消息应含原文: " + event.getErrorMessage(),
                event.getErrorMessage().contains("boom:o-3"));
    }

    @Test
    public void innerInterceptorFailureStillProducesOneFailureEvent() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(AnnotatedService.class, "cancel",
                new Object[]{"o-4"}, new AuditInterceptor(), new VetoInterceptor());
        IllegalStateException caught = null;
        try {
            inv.invoke();
        } catch (IllegalStateException e) {
            caught = e;
        }

        Assert.assertNotNull("内层否决异常应上抛", caught);
        Assert.assertEquals("veto", caught.getMessage());
        Assert.assertEquals("采集在外层，仍应产 1 条失败事件", 1, sink.count());
        Assert.assertTrue(sink.first().getResult().isFailure());
    }
}
