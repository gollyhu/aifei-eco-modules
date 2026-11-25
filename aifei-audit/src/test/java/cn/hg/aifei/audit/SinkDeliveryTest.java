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
import cn.hg.aifei.audit.support.FailingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 落点交付用例。
 */
public class SinkDeliveryTest {

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
     * 被采集的业务对象：正常返回与必抛异常各一个。
     */
    public static class Service {

        static IllegalStateException thrown;

        @Audit(action = "order.cancel", desc = "取消订单")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }

        @Audit(action = "order.boom", desc = "取消失败")
        public String boom(String orderId) {
            thrown = new IllegalStateException("boom:" + orderId);
            throw thrown;
        }
    }

    @Test
    public void switchingSinkKeepsBusinessCodeUntouched() throws Exception {
        CountingSink a = new CountingSink();
        CountingSink b = new CountingSink();

        AuditTestSupport.install(AuditTestSupport.runtime(a, null, null, null, null));
        for (int i = 0; i < 3; i++) {
            AuditTestSupport.invocation(Service.class, "cancel", new Object[]{"o-" + i},
                    new AuditInterceptor()).invoke();
        }
        Assert.assertEquals(3, a.count());
        Assert.assertEquals("未装配的落点不得收到事件", 0, b.count());

        AuditTestSupport.install(AuditTestSupport.runtime(b, null, null, null, null));
        for (int i = 0; i < 3; i++) {
            AuditTestSupport.invocation(Service.class, "cancel", new Object[]{"n-" + i},
                    new AuditInterceptor()).invoke();
        }
        Assert.assertEquals("换落点后事件全进新落点", 3, b.count());
        Assert.assertEquals("旧落点不再收到事件", 3, a.count());
    }

    @Test
    public void startedWithoutSinkCollectsNothingAndLogsNothing() throws Exception {
        AuditTestSupport.install(AuditTestSupport.runtime(null, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-9"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals("无落点：业务返回值不变", "cancelled-o-9", inv.getReturnValue());
        AuditKit.record("order.batch", "无落点静默");
        Assert.assertEquals("运行期不重复记日志", 0, RecordingLogFactory.count("warn"));
    }

    @Test
    public void failingSinkIsSwallowedWithExactlyOneWarning() throws Exception {
        FailingSink sink = new FailingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.fail-fast", "false"));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-6"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.attempts());
        Assert.assertEquals("业务返回值与无落点时相同", "cancelled-o-6", inv.getReturnValue());
        Assert.assertEquals("一次失败只记一条告警", 1, RecordingLogFactory.count("warn"));
    }

    @Test
    public void failFastRethrowsSinkExceptionAfterDelivery() throws Exception {
        IllegalStateException sinkThrown = new IllegalStateException("sink-boom");
        CountingSink sink = CountingSink.recordingThenFailing(sinkThrown);
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.fail-fast", "true"));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-7"}, new AuditInterceptor());
        RuntimeException caught = null;
        try {
            inv.invoke();
        } catch (RuntimeException e) {
            caught = e;
        }

        Assert.assertSame("上抛的应是落点异常本身", sinkThrown, caught);
        Assert.assertEquals("事件已先交付落点", 1, sink.count());
        Assert.assertEquals("order.cancel", sink.first().getAction());
    }

    @Test
    public void failFastKeepsBusinessExceptionPriority() throws Exception {
        IllegalStateException sinkThrown = new IllegalStateException("sink-boom");
        CountingSink sink = CountingSink.recordingThenFailing(sinkThrown);
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.fail-fast", "true"));

        Invocation inv = AuditTestSupport.invocation(Service.class, "boom",
                new Object[]{"o-8"}, new AuditInterceptor());
        IllegalStateException caught = null;
        try {
            inv.invoke();
        } catch (IllegalStateException e) {
            caught = e;
        }

        Assert.assertNotNull(caught);
        Assert.assertSame("业务原异常优先（同一实例）", Service.thrown, caught);
        Assert.assertNotSame(sinkThrown, caught);
        Assert.assertEquals(1, sink.count());
        Assert.assertEquals("落点异常被吞并记一条", 1, RecordingLogFactory.count("warn"));
    }
}
