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
 * 脱敏与截断用例。
 */
public class MaskingTest {

    private static final String SECRET = "SECRET-XYZ";

    private static final String VISIBLE = "VISIBLE-OK";

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
     * 入参形态固定的业务对象。
     */
    public static class MaskService {

        @Audit(action = "order.masked", desc = "脱敏对照", args = true, mask = {1})
        public String masked(String keep, String secret) {
            return "ok";
        }

        @Audit(action = "order.default", desc = "默认不记录")
        public String defaultArgs(String secret) {
            return "ok";
        }

        @Audit(action = "order.long", desc = "超长入参", args = true)
        public String longArgs(String text) {
            return "ok";
        }

        @Audit(action = "order.out-of-range", desc = "越界下标", args = true, mask = {9})
        public String outOfRangeMask(String keep, String secret) {
            return "ok";
        }
    }

    @Test
    public void defaultConfigKeepsArgumentsOutOfEvent() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(MaskService.class, "defaultArgs",
                new Object[]{SECRET}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals(1, sink.count());
        AuditEvent event = sink.first();
        Assert.assertNull("默认不记录入参", event.getArgsSummary());
        Assert.assertFalse("toString 不得含入参原文", event.toString().contains(SECRET));
        Assert.assertTrue("toString 应含动作码", event.toString().contains("order.default"));
        Assert.assertTrue("toString 应含事件号", event.toString().contains(event.getEventId()));
    }

    @Test
    public void maskRemovesOnlyTheConfiguredPosition() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(MaskService.class, "masked",
                new Object[]{VISIBLE, SECRET}, new AuditInterceptor());
        inv.invoke();

        String summary = sink.first().getArgsSummary();
        Assert.assertNotNull("开启参数记录后应有摘要", summary);
        Assert.assertTrue("摘要应保留未剔除参数: " + summary, summary.contains(VISIBLE));
        Assert.assertFalse("摘要不得含被剔除参数原文: " + summary, summary.contains(SECRET));
        Assert.assertTrue("摘要应标识被剔除的参数位: " + summary, summary.contains("<masked>"));
    }

    @Test
    public void globalSwitchAlsoEnablesArgumentSummary() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.args.enabled", "true"));

        Invocation inv = AuditTestSupport.invocation(MaskService.class, "defaultArgs",
                new Object[]{SECRET}, new AuditInterceptor());
        inv.invoke();

        String summary = sink.first().getArgsSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue("全局开关打开后入参可见: " + summary, summary.contains(SECRET));
    }

    @Test
    public void longArgumentIsTruncatedNotRejected() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            builder.append('a');
        }
        Invocation inv = AuditTestSupport.invocation(MaskService.class, "longArgs",
                new Object[]{builder.toString()}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals("ok", inv.getReturnValue());
        Assert.assertEquals(1, sink.count());
        String summary = sink.first().getArgsSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue("摘要长度应 <= 512，实际 " + summary.length(), summary.length() <= 512);
    }

    @Test
    public void outOfRangeMaskIndexIsIgnored() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        Invocation inv = AuditTestSupport.invocation(MaskService.class, "outOfRangeMask",
                new Object[]{VISIBLE, SECRET}, new AuditInterceptor());
        inv.invoke();

        String summary = sink.first().getArgsSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue("越界下标不得剔除任何参数: " + summary,
                summary.contains(VISIBLE) && summary.contains(SECRET));
    }
}
