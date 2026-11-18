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
import java.util.function.Supplier;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 注入值用例：AC-20（链路标识 / 客户端标识由消费方注入，模块零生态依赖）。
 */
public class TraceSupplierTest {

    private static final String FIXED_TRACE = "trace-fixed-001";

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
     * 被采集的业务对象。
     */
    public static class Service {

        @Audit(action = "order.cancel", desc = "取消订单")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }
    }

    @Test
    public void injectedTraceIdLandsOnEventVerbatim() throws Exception {
        CountingSink sink = new CountingSink();
        Supplier<String> trace = new Supplier<String>() {
            @Override
            public String get() {
                return FIXED_TRACE;
            }
        };
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, trace, null));

        AuditTestSupport.invocation(Service.class, "cancel", new Object[]{"o-1"},
                new AuditInterceptor()).invoke();

        Assert.assertEquals(FIXED_TRACE, sink.first().getTraceId());
    }

    @Test
    public void missingSupplierLeavesTraceIdEmptyWithoutFailure() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-2"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.count());
        Assert.assertNull(sink.first().getTraceId());
        Assert.assertEquals("cancelled-o-2", inv.getReturnValue());
        Assert.assertEquals("未注入不应记日志", 0, RecordingLogFactory.count("warn"));
    }

    @Test
    public void clientSupplierIsUsedOnlyWhenEnabled() throws Exception {
        Supplier<String> client = new Supplier<String>() {
            @Override
            public String get() {
                return "client-42";
            }
        };

        CountingSink disabled = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(disabled, null, null, null, client));
        AuditTestSupport.invocation(Service.class, "cancel", new Object[]{"o-3"},
                new AuditInterceptor()).invoke();
        Assert.assertNull("未开启客户端采集时不得调用取值器", disabled.first().getClient());

        CountingSink enabled = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(enabled, null, null, null, client,
                "audit.client.enabled", "true"));
        AuditTestSupport.invocation(Service.class, "cancel", new Object[]{"o-4"},
                new AuditInterceptor()).invoke();
        Assert.assertEquals("client-42", enabled.first().getClient());
    }

    @Test
    public void throwingSupplierDoesNotBreakBusiness() throws Exception {
        CountingSink sink = new CountingSink();
        Supplier<String> broken = new Supplier<String>() {
            @Override
            public String get() {
                throw new IllegalStateException("supplier-failure");
            }
        };
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, broken, null));

        Invocation inv = AuditTestSupport.invocation(Service.class, "cancel",
                new Object[]{"o-5"}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals("cancelled-o-5", inv.getReturnValue());
        Assert.assertEquals(1, sink.count());
        Assert.assertNull(sink.first().getTraceId());
        Assert.assertEquals(1, RecordingLogFactory.count("warn"));
    }
}
