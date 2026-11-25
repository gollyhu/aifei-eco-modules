/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import cn.aifei.aop.Invocation;
import cn.aifei.log.LogFactory;
import cn.hg.aifei.audit.aifei.AuditInterceptor;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.support.AuditTestSupport;
import cn.hg.aifei.audit.support.CountingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 手动埋点用例。
 */
public class ManualRecordTest {

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
     * 无注解的业务对象，供对照。
     */
    public static class PlainService {

        @Audit(action = "order.cancel")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }

        public String plain(String orderId) {
            return "plain-" + orderId;
        }
    }

    @Test
    public void manualEventUsesSameSinkAndSameShape() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.module", "demo-module"));

        AuditKit.record("order.batch", "批量任务项", "item-1");

        Assert.assertEquals(1, sink.count());
        AuditEvent event = sink.first();
        Assert.assertEquals("order.batch", event.getAction());
        Assert.assertEquals("批量任务项", event.getDesc());
        Assert.assertEquals("item-1", event.getTarget());
        Assert.assertEquals("demo-module", event.getModule());
        Assert.assertEquals("手动埋点耗时恒 0", 0L, event.getElapsedMillis());
        Assert.assertNull("手动埋点不带入参摘要", event.getArgsSummary());
        Assert.assertFalse(event.getResult().isFailure());

        AuditKit.recordFailure("order.batch", "批量任务项", null, "E-500", "业务码失败");
        AuditEvent failure = sink.last();
        Assert.assertEquals(2, sink.count());
        Assert.assertTrue(failure.getResult().isFailure());
        Assert.assertEquals("E-500", failure.getErrorCode());
        Assert.assertEquals("业务码失败", failure.getErrorMessage());
    }

    @Test
    public void beforeStartManualRecordThrowsWhileAnnotationPathIsSilent() throws Exception {
        AuditHolder.clear();

        AuditException caught = null;
        try {
            AuditKit.record("order.batch", "未启动");
        } catch (AuditException e) {
            caught = e;
        }
        Assert.assertNotNull("未启动时手动埋点必须抛模块异常", caught);
        Assert.assertTrue("消息应含「插件未启动或已停止」: " + caught.getMessage(),
                caught.getMessage().contains("插件未启动或已停止"));

        IllegalArgumentException blank = null;
        try {
            AuditKit.record("  ", "空白动作码");
        } catch (IllegalArgumentException e) {
            blank = e;
        }
        Assert.assertNotNull("空白动作码应先抛参数异常", blank);

        IllegalArgumentException nullError = null;
        try {
            AuditKit.recordFailure("order.batch", "失败", null, (Throwable) null);
        } catch (IllegalArgumentException e) {
            nullError = e;
        }
        Assert.assertNotNull("error 为 null 应抛参数异常", nullError);

        Invocation inv = AuditTestSupport.invocation(PlainService.class, "cancel",
                new Object[]{"o-5"}, new AuditInterceptor());
        inv.invoke();
        Assert.assertEquals("未启动时注解路径零采集、业务返回不变", "cancelled-o-5", inv.getReturnValue());
        Assert.assertNull("未启动不装快照", AuditHolder.snapshot());
    }
}
