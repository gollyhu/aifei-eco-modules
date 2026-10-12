/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.sentinel.core;

import cn.aifei.util.Prop;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 两种互斥模式测试：由 sentinel.dashboard.server 决定分流。
 *  - 本地模式：热读规则文件、不回写；
 *  - Dashboard 模式：不热读、规则变更回写本地文件、缺 app.name 报错。
 */
public class SentinelKitModeTest {

    public static class Svc {
        public String run() {
            return "ok";
        }
    }

    private Path rulesFile;

    @Before
    public void setUp() throws Exception {
        SentinelKit.shutdown();
        FlowRuleManager.loadRules(Collections.<FlowRule>emptyList());
        rulesFile = Files.createTempFile("sentinel-rules", ".json");
        Files.write(rulesFile, "{}".getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() throws Exception {
        SentinelKit.shutdown();
        FlowRuleManager.loadRules(Collections.<FlowRule>emptyList());
        Files.deleteIfExists(rulesFile);
        System.clearProperty("csp.sentinel.dashboard.server");
        System.clearProperty("project.name");
    }

    @Test
    public void localModeRegistersHotReadAndDoesNotWriteBack() throws Exception {
        SentinelKit.loadRules(localProp());
        Assert.assertEquals("本地模式应注册五类规则热读数据源", 5, dataSources().size());

        FlowRuleManager.loadRules(Collections.singletonList(flowRule(Svc.class.getName() + "#run", 7.0)));
        Assert.assertEquals("本地模式不应回写规则文件", "{}", readFile());
    }

    @Test
    public void dashboardModeWritesBackOnRuleChange() throws Exception {
        SentinelKit.loadRules(dashboardProp("demo"));
        Assert.assertEquals("Dashboard 模式不应注册热读数据源", 0, dataSources().size());

        FlowRuleManager.loadRules(Collections.singletonList(flowRule(Svc.class.getName() + "#run", 7.0)));

        String content = readFile();
        Assert.assertTrue("应回写 flowRules，实际：" + content, content.contains("flowRules"));
        Assert.assertTrue("应含资源名，实际：" + content, content.contains(Svc.class.getName() + "#run"));
    }

    @Test
    public void dashboardModeWithoutAppNameFailsFast() {
        Prop prop = dashboardProp(null);
        try {
            SentinelKit.loadRules(prop);
            Assert.fail("应抛 IllegalStateException");
        } catch (IllegalStateException expected) {
            Assert.assertTrue("应提示缺少 app.name，实际：" + expected.getMessage(),
                    expected.getMessage().contains("sentinel.app.name"));
        }
    }

    // ---- helpers ----

    private Prop localProp() {
        Prop prop = new Prop();
        prop.getProperties().setProperty("sentinel.rules.file", rulesFile.toString());
        prop.getProperties().setProperty("sentinel.rules.refreshMs", "60000");
        return prop;
    }

    private Prop dashboardProp(String appName) {
        Prop prop = localProp();
        prop.getProperties().setProperty("sentinel.dashboard.server", "127.0.0.1:8858");
        if (appName != null) {
            prop.getProperties().setProperty("sentinel.app.name", appName);
        }
        return prop;
    }

    private static FlowRule flowRule(String resource, double count) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(count);
        return rule;
    }

    private String readFile() throws Exception {
        return new String(Files.readAllBytes(rulesFile), StandardCharsets.UTF_8).trim();
    }

    @SuppressWarnings("unchecked")
    private static List<?> dataSources() throws Exception {
        Field field = SentinelKit.class.getDeclaredField("DATA_SOURCES");
        field.setAccessible(true);
        return (List<?>) field.get(null);
    }
}
