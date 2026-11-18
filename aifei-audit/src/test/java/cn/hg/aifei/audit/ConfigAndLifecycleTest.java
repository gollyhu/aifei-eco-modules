/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import cn.aifei.aop.AopKit;
import cn.aifei.aop.Invocation;
import cn.aifei.log.LogFactory;
import cn.hg.aifei.audit.aifei.AuditInterceptor;
import cn.hg.aifei.audit.aifei.AuditPlugin;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.internal.AuditSpi;
import cn.hg.aifei.audit.internal.AuditRuntime;
import cn.hg.aifei.audit.spi.AuditSink;
import cn.hg.aifei.audit.support.AuditTestSupport;
import cn.hg.aifei.audit.support.CountingSink;
import cn.hg.aifei.audit.support.RecordingLogFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * 装配与生命周期用例：AC-09 / AC-18 / AC-18b / AC-19 / AC-29（含三态与无半装配）。
 *
 * <p>方法按名称升序执行：首个用例断言「容器内无落点」的启动态，之后的用例注册唯一落点。
 * 框架拒绝同类型二次注册，故落点只在此类注册一次。</p>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConfigAndLifecycleTest {

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
     * 合法业务对象。
     */
    public static class LifecycleService {

        @Audit(action = "order.cancel", desc = "取消订单")
        public String cancel(String orderId) {
            return "cancelled-" + orderId;
        }
    }

    /**
     * 空动作码业务对象：启动期校验必须拦下。
     */
    public static class EmptyActionService {

        @Audit(action = "  ")
        public String run() {
            return "should-not-run";
        }
    }

    @Test
    public void a01StartWithoutSinkWarnsOnceAndCollectsNothing() throws Exception {
        AuditHolder.clear();
        Assert.assertNull("用例前置：容器内不得有落点实现", AuditSpi.sink(AuditTestSupport.emptyEnvironment()));

        AuditPlugin plugin = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", LifecycleService.class.getName()));
        plugin.start();

        Assert.assertEquals("已启用但无落点：启动阶段恰一条告警", 1, RecordingLogFactory.count("warn"));
        AuditRuntime runtime = AuditHolder.snapshot();
        Assert.assertNotNull("应安装快照（三态②）", runtime);
        Assert.assertNull(runtime.sink());

        Invocation inv = AuditTestSupport.invocation(LifecycleService.class, "cancel",
                new Object[]{"o-1"}, new AuditInterceptor());
        inv.invoke();
        Assert.assertEquals("无落点：业务返回值不变", "cancelled-o-1", inv.getReturnValue());
        AuditKit.record("order.batch", "无落点静默返回");
        Assert.assertEquals("运行期不重复记日志", 1, RecordingLogFactory.count("warn"));

        plugin.stop();
        Assert.assertNull("停止后快照清除", AuditHolder.snapshot());

        AuditException stopped = null;
        try {
            AuditKit.record("order.batch", "已停止");
        } catch (AuditException e) {
            stopped = e;
        }
        Assert.assertNotNull("停止后手动埋点抛模块异常", stopped);
        Assert.assertTrue(stopped.getMessage().contains("插件未启动或已停止"));

        Invocation afterStop = AuditTestSupport.invocation(LifecycleService.class, "cancel",
                new Object[]{"o-2"}, new AuditInterceptor());
        afterStop.invoke();
        Assert.assertEquals("停止后注解路径零采集、返回值不变", "cancelled-o-2", afterStop.getReturnValue());

        plugin.stop();
        plugin.stop();
    }

    @Test
    public void a02StartWithSinkCollectsAndStopEndsCollection() throws Exception {
        CountingSink shared = new CountingSink();
        AopKit.get().addSingletonObject(AuditSink.class, shared);

        AuditPlugin plugin = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", LifecycleService.class.getName(),
                "audit.module", "demo-module"));
        plugin.start();
        Assert.assertEquals("有落点时不产生启动告警", 0, RecordingLogFactory.count("warn"));

        AuditTestSupport.invocation(LifecycleService.class, "cancel", new Object[]{"o-3"},
                new AuditInterceptor()).invoke();
        Assert.assertEquals(1, shared.count());
        Assert.assertEquals("demo-module", shared.first().getModule());

        plugin.stop();
        AuditTestSupport.invocation(LifecycleService.class, "cancel", new Object[]{"o-4"},
                new AuditInterceptor()).invoke();
        Assert.assertEquals("停止后不再产事件", 1, shared.count());

        plugin.start();
        Assert.assertEquals("重复 start() 幂等且可再次生效", 0, RecordingLogFactory.count("warn"));
        AuditTestSupport.invocation(LifecycleService.class, "cancel", new Object[]{"o-5"},
                new AuditInterceptor()).invoke();
        Assert.assertEquals(2, shared.count());
        plugin.stop();
    }

    @Test
    public void b01InvalidConfigFailsFastWithoutResidue() throws Exception {
        AuditHolder.clear();

        AuditPlugin badLength = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", LifecycleService.class.getName(),
                "audit.args.max-length", "-1"));
        AuditException lengthFailure = null;
        try {
            badLength.start();
        } catch (AuditException e) {
            lengthFailure = e;
        }
        Assert.assertNotNull("负数上限应启动失败", lengthFailure);
        Assert.assertTrue("消息应含键名: " + lengthFailure.getMessage(),
                lengthFailure.getMessage().contains("audit.args.max-length"));
        Assert.assertTrue("消息应含原值: " + lengthFailure.getMessage(),
                lengthFailure.getMessage().contains("-1"));
        Assert.assertNull("启动失败不得残留快照", AuditHolder.snapshot());

        AuditPlugin badFlag = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", LifecycleService.class.getName(),
                "audit.fail-fast", "1"));
        AuditException flagFailure = null;
        try {
            badFlag.start();
        } catch (AuditException e) {
            flagFailure = e;
        }
        Assert.assertNotNull("非法布尔值应启动失败", flagFailure);
        Assert.assertTrue(flagFailure.getMessage().contains("audit.fail-fast"));
        Assert.assertTrue(flagFailure.getMessage().contains("1"));
        Assert.assertNull(AuditHolder.snapshot());

        AuditPlugin good = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", LifecycleService.class.getName()));
        good.start();
        Assert.assertNotNull("合法配置可再次启动", AuditHolder.snapshot());
        good.stop();
    }

    @Test
    public void c01EmptyActionFailsStartAndReportsSignature() {
        AuditHolder.clear();

        AuditPlugin plugin = new AuditPlugin(AuditTestSupport.prop())
                .addScanClasses(EmptyActionService.class);
        AuditException failure = null;
        try {
            plugin.start();
        } catch (AuditException e) {
            failure = e;
        }
        Assert.assertNotNull("空动作码应启动失败", failure);
        String signature = EmptyActionService.class.getName() + "#run";
        Assert.assertTrue("消息应含类名#方法名: " + failure.getMessage(),
                failure.getMessage().contains(signature));
        Assert.assertNull("启动失败不得残留快照", AuditHolder.snapshot());

        AuditPlugin good = new AuditPlugin(AuditTestSupport.prop())
                .addScanClasses(LifecycleService.class);
        good.start();
        Assert.assertNotNull("改用合法扫描范围可正常启动", AuditHolder.snapshot());
        good.stop();
    }

    @Test
    public void d01ScanRangeIsMandatory() {
        AuditHolder.clear();

        AuditPlugin plugin = new AuditPlugin(AuditTestSupport.prop());
        AuditException failure = null;
        try {
            plugin.start();
        } catch (AuditException e) {
            failure = e;
        }
        Assert.assertNotNull("未提供扫描范围应启动失败", failure);
        Assert.assertTrue("消息应说明扫描范围缺失: " + failure.getMessage(),
                failure.getMessage().contains("未提供注解扫描范围"));
        Assert.assertNull(AuditHolder.snapshot());
    }

    @Test
    public void e01DisabledPluginInstallsNoSnapshot() throws Exception {
        AuditHolder.clear();

        AuditPlugin plugin = new AuditPlugin(AuditTestSupport.prop(
                "audit.scan.classes", LifecycleService.class.getName(),
                "audit.enabled", "false"));
        plugin.start();
        Assert.assertNull("关闭采集时不装快照", AuditHolder.snapshot());

        Invocation inv = AuditTestSupport.invocation(LifecycleService.class, "cancel",
                new Object[]{"o-6"}, new AuditInterceptor());
        inv.invoke();
        Assert.assertEquals("cancelled-o-6", inv.getReturnValue());

        AuditException failure = null;
        try {
            AuditKit.record("order.batch", "关闭采集");
        } catch (AuditException e) {
            failure = e;
        }
        Assert.assertNotNull("关闭采集等价未启动", failure);
        plugin.stop();
    }
}
