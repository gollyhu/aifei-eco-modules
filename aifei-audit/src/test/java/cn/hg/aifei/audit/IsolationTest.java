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
import cn.hg.aifei.audit.internal.ResultPolicy;
import cn.hg.aifei.audit.support.AuditTestSupport;
import cn.hg.aifei.audit.support.CountingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import cn.hg.aifei.audit.support.StubActorProvider;
import cn.hg.aifei.audit.support.StubResultJudge;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 失败隔离用例。
 */
public class IsolationTest {

    private static LogFactory previous;

    @BeforeClass
    public static void setUpClass() {
        previous = RecordingLogFactory.install();
    }

    @AfterClass
    public static void tearDownClass() {
        RecordingLogFactory.restore(previous);
    }

    @Before
    public void setUp() {
        RecordingLogFactory.clear();
    }

    @After
    public void tearDown() {
        AuditHolder.clear();
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
    public void missingActorProviderKeepsEventWithoutActor() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-1"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.count());
        Assert.assertNull(sink.first().getActorId());
        Assert.assertNull(sink.first().getActorName());
        Assert.assertEquals("cancelled-o-1", inv.getReturnValue());
        Assert.assertEquals("无 provider 不应记日志", 0, RecordingLogFactory.count("warn"));
    }

    @Test
    public void actorProviderFailureIsSwallowedWithOneWarning() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, StubActorProvider.failingId(),
                null, null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-2"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.count());
        Assert.assertNull(sink.first().getActorId());
        Assert.assertNull(sink.first().getActorName());
        Assert.assertEquals("cancelled-o-2", inv.getReturnValue());
        Assert.assertEquals("一次失败只记一条告警", 1, RecordingLogFactory.count("warn"));
    }

    @Test
    public void actorNameFailureKeepsIdAndLogsOnce() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, StubActorProvider.failingName(),
                null, null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-3"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.count());
        Assert.assertEquals("alice", sink.first().getActorId());
        Assert.assertNull(sink.first().getActorName());
        Assert.assertEquals(1, RecordingLogFactory.count("warn"));
    }

    @Test
    public void judgeFailureFallsBackToDefaultResult() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, StubResultJudge.throwing(),
                null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-4"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.count());
        Assert.assertFalse("正常返回且判定不可用时应为成功", sink.first().getResult().isFailure());
        Assert.assertNull(sink.first().getErrorCode());
        Assert.assertEquals("cancelled-o-4", inv.getReturnValue());
        Assert.assertEquals(1, RecordingLogFactory.count("warn"));
    }

    @Test
    public void judgeCanDowngradeSuccessfulReturnToFailure() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, StubResultJudge.failure(),
                null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-5"}, new AuditInterceptor());
        inv.invoke();

        AuditEvent event = sink.first();
        Assert.assertTrue(event.getResult().isFailure());
        Assert.assertEquals(ResultPolicy.BUSINESS_CODE_FAILURE, event.getErrorCode());
        Assert.assertEquals("判定失败不改业务返回值", "cancelled-o-5", inv.getReturnValue());
    }
}
