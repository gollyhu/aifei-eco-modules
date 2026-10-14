/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.core;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.FileRefreshableDataSource;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 规则加载与热生效：
 *  - load(...)：一次性加载，用于 classpath 资源或无需监控的场景；
 *  - watch(file, refreshMs)：基于 FileRefreshableDataSource，规则文件变化后自动刷新。
 */
public final class SentinelRuleLoader {

    private static final int DEFAULT_BUF_SIZE = 1024 * 1024;

    public static void load(Path file) {
        try {
            load(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取 Sentinel 规则文件失败: " + file, e);
        }
    }

    public static void load(ClassLoader classLoader, String resource) {
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("未找到 Sentinel 规则资源: " + resource);
            }
            load(new String(readFully(in), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取 Sentinel 规则资源失败: " + resource, e);
        }
    }

    /** 解析 JSON 字符串并一次性加载全部类型规则 */
    public static void load(String json) {
        JSONObject root = JSON.parseObject(json);

        List<FlowRule> flowRules = toList(root, "flowRules", FlowRule.class);
        if (!flowRules.isEmpty()) {
            FlowRuleManager.loadRules(flowRules);
        }
        List<DegradeRule> degradeRules = toList(root, "degradeRules", DegradeRule.class);
        if (!degradeRules.isEmpty()) {
            DegradeRuleManager.loadRules(degradeRules);
        }
        List<SystemRule> systemRules = toList(root, "systemRules", SystemRule.class);
        if (!systemRules.isEmpty()) {
            SystemRuleManager.loadRules(systemRules);
        }
        List<ParamFlowRule> paramFlowRules = toList(root, "paramFlowRules", ParamFlowRule.class);
        if (!paramFlowRules.isEmpty()) {
            ParamFlowRuleManager.loadRules(paramFlowRules);
        }
        List<AuthorityRule> authorityRules = toList(root, "authorityRules", AuthorityRule.class);
        if (!authorityRules.isEmpty()) {
            AuthorityRuleManager.loadRules(authorityRules);
        }
    }

    /** 监听规则文件，文件变化后自动刷新对应规则；返回的数据源须在应用关闭时 close */
    public static List<FileRefreshableDataSource<?>> watch(Path file, int refreshMs) {
        List<FileRefreshableDataSource<?>> sources = new ArrayList<>(5);
        try {
            FileRefreshableDataSource<List<FlowRule>> flow = createDataSource(file, refreshMs, "flowRules", FlowRule.class);
            FlowRuleManager.register2Property(flow.getProperty());
            sources.add(flow);

            FileRefreshableDataSource<List<DegradeRule>> degrade = createDataSource(file, refreshMs, "degradeRules", DegradeRule.class);
            DegradeRuleManager.register2Property(degrade.getProperty());
            sources.add(degrade);

            FileRefreshableDataSource<List<SystemRule>> system = createDataSource(file, refreshMs, "systemRules", SystemRule.class);
            SystemRuleManager.register2Property(system.getProperty());
            sources.add(system);

            FileRefreshableDataSource<List<ParamFlowRule>> paramFlow = createDataSource(file, refreshMs, "paramFlowRules", ParamFlowRule.class);
            ParamFlowRuleManager.register2Property(paramFlow.getProperty());
            sources.add(paramFlow);

            FileRefreshableDataSource<List<AuthorityRule>> authority = createDataSource(file, refreshMs, "authorityRules", AuthorityRule.class);
            AuthorityRuleManager.register2Property(authority.getProperty());
            sources.add(authority);

        } catch (FileNotFoundException e) {
            closeQuietly(sources);
            throw new IllegalStateException("创建 Sentinel 规则数据源失败: " + file, e);
        }
        return sources;
    }

    /** 五参构造显式指定刷新间隔，避免三参构造的 int 被误当作 bufSize */
    private static <T> FileRefreshableDataSource<List<T>> createDataSource(
            Path file, int refreshMs, String key, Class<T> type) throws FileNotFoundException {
        return new FileRefreshableDataSource<List<T>>(
                file.toFile(),
                converter(key, type),
                refreshMs,
                DEFAULT_BUF_SIZE,
                StandardCharsets.UTF_8);
    }

    private static <T> Converter<String, List<T>> converter(String key, Class<T> type) {
        return source -> toList(JSON.parseObject(source), key, type);
    }

    private static <T> List<T> toList(JSONObject root, String key, Class<T> type) {
        JSONArray array = root.getJSONArray(key);
        return array == null ? Collections.<T>emptyList() : array.toJavaList(type);
    }

    static void closeQuietly(List<? extends FileRefreshableDataSource<?>> sources) {
        for (FileRefreshableDataSource<?> ds : sources) {
            try {
                ds.close();
            } catch (Exception ignored) {
                // 关闭失败不影响主流程
            }
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private SentinelRuleLoader() {}
}
