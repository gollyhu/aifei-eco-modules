/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import cn.aifei.aop.Invocation;
import cn.aifei.log.LogFactory;
import cn.hg.aifei.audit.aifei.AuditInterceptor;
import cn.hg.aifei.audit.aifei.AuditPlugin;
import cn.hg.aifei.audit.internal.AuditAnnotations;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.internal.AuditRuntime;
import cn.hg.aifei.audit.support.AuditTestSupport;
import cn.hg.aifei.audit.support.CountingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import cn.hg.aifei.audit.support.StubActorProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * 验收补测：覆盖既有用例未逐项固化的断言点。
 *
 * <p>补测范围：AC-04 全要素取值、AC-07 同 sink / 同类型、AC-09b 启动失败态、
 * AC-13 toString 真覆写、AC-14 0-based 下标、AC-15 上限 0 边界、
 * AC-18 重复 start 幂等、AC-29 注解四属性运行期可读。</p>
 */
public class AcceptanceSupplementTest {

    private static final String SECRET = "SECRET-XYZ";

    private static final String VISIBLE = "VISIBLE-OK";

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

    @Before
    public void setUp() {
        RecordingLogFactory.clear();
        AuditHolder.clear();
    }

    @After
    public void tearDown() {
        AuditHolder.clear();
    }

    /**
     * AC-04 全要素的取材对象。
     */
    public static class ElementService {

        @Audit(action = "order.cancel", desc = "取消订单")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }

        @Audit(action = "order.args", desc = "记录入参", args = true)
        public String withArgs(String orderId) {
            return "ok";
        }
    }

    /**
     * AC-14 0-based 下标对象：剔除索引 0（第一个参数）。
     */
    public static class ZeroBasedService {

        @Audit(action = "order.zero", desc = "0-based 剔除", args = true, mask = {0})
        public String run(String secret, String keep) {
            return "ok";
        }
    }

    /**
     * AC-15 超长入参对象。
     */
    public static class LongService {

        @Audit(action = "order.long", desc = "超长入参", args = true)
        public String run(String text) {
            return "ok";
        }
    }

    @Test
    public void ac04AnnotationPathFillsEveryElement() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, StubActorProvider.of("alice", "Alice"),
                null, fixed(FIXED_TRACE), fixed("client-9"),
                "audit.module", "demo-module", "audit.client.enabled", "true"));

        Invocation inv = AuditTestSupport.invocation(ElementService.class, "cancel",
                new Object[]{"o-1"}, new AuditInterceptor());
        inv.invoke();

        AuditEvent event = sink.first();
        Assert.assertNotNull(event);
        Assert.assertEquals(1, sink.count());
        Assert.assertFalse("事件号不得为空白", event.getEventId().trim().isEmpty());
        Assert.assertTrue("发生时刻应大于 0", event.getOccurredAt() > 0L);
        Assert.assertEquals("动作码取注解值", "order.cancel", event.getAction());
        Assert.assertEquals("业务模块取配置值", "demo-module", event.getModule());
        Assert.assertEquals("描述取注解值", "取消订单", event.getDesc());
        Assert.assertEquals("alice", event.getActorId());
        Assert.assertEquals("Alice", event.getActorName());
        Assert.assertNull("注解路径无目标对象标识", event.getTarget());
        Assert.assertNull("默认不记录入参", event.getArgsSummary());
        Assert.assertEquals(AuditEvent.Result.SUCCESS, event.getResult());
        Assert.assertNull(event.getErrorCode());
        Assert.assertNull(event.getErrorMessage());
        Assert.assertTrue("耗时不得为负", event.getElapsedMillis() >= 0L);
        Assert.assertEquals(FIXED_TRACE, event.getTraceId());
        Assert.assertEquals("client-9", event.getClient());
    }

    @Test
    public void ac04ArgumentSummaryFollowsRecordingSwitch() throws Exception {
        CountingSink annotationOn = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(annotationOn, null, null, null, null));
        AuditTestSupport.invocation(ElementService.class, "withArgs",
                new Object[]{"o-2"}, new AuditInterceptor()).invoke();
        String override = annotationOn.first().getArgsSummary();
        Assert.assertNotNull("注解 args=true 可逐方法覆盖全局默认", override);
        Assert.assertTrue("摘要应含入参原文: " + override, override.contains("o-2"));

        CountingSink annotationOff = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(annotationOff, null, null, null, null,
                "audit.args.enabled", "true"));
        AuditTestSupport.invocation(ElementService.class, "cancel",
                new Object[]{"o-3"}, new AuditInterceptor()).invoke();
        String global = annotationOff.first().getArgsSummary();
        Assert.assertNotNull("全局开关打开后应生成入参摘要", global);
        Assert.assertTrue("摘要应含入参原文: " + global, global.contains("o-3"));
    }

    @Test
    public void ac07ManualAndAnnotationShareSinkAndEventType() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.module", "demo-module"));

        AuditTestSupport.invocation(ElementService.class, "cancel",
                new Object[]{"o-4"}, new AuditInterceptor()).invoke();
        AuditKit.record("order.batch", "批量任务项", "item-1");

        Assert.assertEquals("两条路径落到同一落点", 2, sink.count());
        AuditEvent annotated = sink.events().get(0);
        AuditEvent manual = sink.events().get(1);
        Assert.assertSame("两条路径产出同一事件类型", annotated.getClass(), manual.getClass());
        Assert.assertEquals(AuditEvent.class, manual.getClass());
        Assert.assertEquals("order.batch", manual.getAction());
        Assert.assertEquals("批量任务项", manual.getDesc());
        Assert.assertEquals("item-1", manual.getTarget());
        Assert.assertEquals("demo-module", manual.getModule());
        Assert.assertFalse(manual.getResult().isFailure());
        Assert.assertEquals("手动埋点耗时恒 0", 0L, manual.getElapsedMillis());
        Assert.assertNull("手动埋点不带入参摘要", manual.getArgsSummary());
    }

    @Test
    public void ac09bFailedStartBehavesAsNotStarted() throws Exception {
        AuditPlugin bad = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", ElementService.class.getName(),
                "audit.args.max-length", "-1"));
        AuditException startFailure = null;
        try {
            bad.start();
        } catch (AuditException e) {
            startFailure = e;
        }
        Assert.assertNotNull("非法配置应启动失败", startFailure);
        Assert.assertNull("启动失败不得残留快照", AuditHolder.snapshot());

        AuditException recordFailure = null;
        try {
            AuditKit.record("order.batch", "启动失败后");
        } catch (AuditException e) {
            recordFailure = e;
        }
        Assert.assertNotNull("启动失败等价未启动：手动埋点抛模块异常", recordFailure);
        Assert.assertTrue("消息应含「插件未启动或已停止」: " + recordFailure.getMessage(),
                recordFailure.getMessage().contains("插件未启动或已停止"));

        Invocation inv = AuditTestSupport.invocation(ElementService.class, "cancel",
                new Object[]{"o-5"}, new AuditInterceptor());
        inv.invoke();
        Assert.assertEquals("启动失败后注解路径零采集、返回值不变", "cancelled-o-5", inv.getReturnValue());
        Assert.assertNull(AuditHolder.snapshot());
    }

    @Test
    public void ac13ToStringIsReallyOverriddenAndHidesSecret() throws Exception {
        Assert.assertEquals("toString 必须由 AuditEvent 自身覆写",
                AuditEvent.class, AuditEvent.class.getMethod("toString").getDeclaringClass());

        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));
        AuditTestSupport.invocation(ElementService.class, "cancel",
                new Object[]{SECRET}, new AuditInterceptor()).invoke();

        AuditEvent event = sink.first();
        Assert.assertNull(event.getArgsSummary());
        String text = event.toString();
        Assert.assertFalse("toString 不得含入参原文", text.contains(SECRET));
        Assert.assertTrue("toString 应含事件号", text.contains(event.getEventId()));
        Assert.assertTrue("toString 应含动作码", text.contains("order.cancel"));
    }

    @Test
    public void ac14MaskIsZeroBased() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null));

        AuditTestSupport.invocation(ZeroBasedService.class, "run",
                new Object[]{SECRET, VISIBLE}, new AuditInterceptor()).invoke();

        String summary = sink.first().getArgsSummary();
        Assert.assertNotNull(summary);
        Assert.assertTrue("索引 0 应被剔除: " + summary, summary.contains("#0=<masked>"));
        Assert.assertFalse("被剔除参数原文不得出现: " + summary, summary.contains(SECRET));
        Assert.assertTrue("索引 1 应保留原文: " + summary, summary.contains(VISIBLE));
    }

    @Test
    public void ac15MaxLengthZeroKeepsPlaceholderWithoutFailure() throws Exception {
        CountingSink sink = new CountingSink();
        AuditTestSupport.install(AuditTestSupport.runtime(sink, null, null, null, null,
                "audit.args.enabled", "true", "audit.args.max-length", "0"));

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            builder.append('a');
        }
        Invocation inv = AuditTestSupport.invocation(LongService.class, "run",
                new Object[]{builder.toString()}, new AuditInterceptor());
        inv.invoke();

        Assert.assertEquals("ok", inv.getReturnValue());
        Assert.assertEquals("事件仍应产出 1 条", 1, sink.count());
        String summary = sink.first().getArgsSummary();
        Assert.assertNotNull("上限 0 时应留占位符", summary);
        Assert.assertTrue("上限 0 的摘要长度应 <= 512，实际 " + summary.length(), summary.length() <= 512);
        Assert.assertEquals("[args omitted]", summary);
    }

    @Test
    public void ac18RepeatedStartKeepsSameSnapshot() {
        AuditPlugin plugin = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", ElementService.class.getName()));
        plugin.start();
        AuditRuntime first = AuditHolder.snapshot();
        Assert.assertNotNull(first);
        int warnsAfterFirst = RecordingLogFactory.count("warn");

        plugin.start();
        Assert.assertSame("重复 start() 不得重建快照", first, AuditHolder.snapshot());
        Assert.assertEquals("重复 start() 不得新增告警", warnsAfterFirst,
                RecordingLogFactory.count("warn"));

        plugin.stop();
        plugin.stop();
        Assert.assertNull("重复 stop() 幂等且清理快照", AuditHolder.snapshot());
    }

    @Test
    public void ac29AnnotationExposesFourRuntimeReadableAttributes() throws Exception {
        Assert.assertEquals(RetentionPolicy.RUNTIME, Audit.class.getAnnotation(Retention.class).value());
        Target target = Audit.class.getAnnotation(Target.class);
        boolean methodTarget = false;
        for (int i = 0; i < target.value().length; i++) {
            if ("METHOD".equals(target.value()[i].name())) {
                methodTarget = true;
            }
        }
        Assert.assertTrue("@Audit 应可标注方法", methodTarget);

        Assert.assertNull("action 必填，不应有默认值", Audit.class.getDeclaredMethod("action").getDefaultValue());
        Assert.assertEquals("", Audit.class.getDeclaredMethod("desc").getDefaultValue());
        Assert.assertEquals(Boolean.FALSE, Audit.class.getDeclaredMethod("args").getDefaultValue());
        int[] mask = (int[]) Audit.class.getDeclaredMethod("mask").getDefaultValue();
        Assert.assertEquals(0, mask.length);

        Method method = ZeroBasedService.class.getMethod("run", String.class, String.class);
        AuditAnnotations.ActionSpec spec = AuditAnnotations.of(method);
        Assert.assertNotNull("@Audit 必须在运行期可读", spec);
        Assert.assertEquals("order.zero", spec.getAction());
        Assert.assertEquals("0-based 剔除", spec.getDesc());
        Assert.assertTrue("args 属性运行期可读", spec.isArgs());
        Assert.assertEquals(1, spec.getMask().length);
        Assert.assertEquals(0, spec.getMask()[0]);
    }

    private static Supplier<String> fixed(final String value) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return value;
            }
        };
    }
}
