/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.sentinel.core;

import cn.aifei.util.Prop;
import com.alibaba.csp.sentinel.datasource.FileRefreshableDataSource;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 消费方唯一装配入口：启动加载规则、关闭释放资源。业务不直调 sentinel 类型。
 * 两种互斥模式由 sentinel.dashboard.server 决定（详见方案 §12）：
 *  - 未配：本地模式，规则文件热读（M1/M2 行为）；
 *  - 已配：Dashboard 模式，注入 Dashboard 属性 + 一次性初始加载 + 回写本地文件（关热读，避免回环）。
 */
public final class SentinelKit {

    private static final String KEY_ENABLE = "sentinel.enable";
    private static final String KEY_RULES_FILE = "sentinel.rules.file";
    private static final String KEY_REFRESH_MS = "sentinel.rules.refreshMs";
    private static final String KEY_DASHBOARD = "sentinel.dashboard.server";
    private static final String KEY_APP_NAME = "sentinel.app.name";
    private static final String KEY_WRITABLE = "sentinel.rules.writable";

    /** transport 模块的标记类：仅用于反射探测，SDK 不 import 任何 transport 类型 */
    private static final String TRANSPORT_MARKER = "com.alibaba.csp.sentinel.transport.config.TransportConfig";

    private static volatile boolean loaded;
    private static final List<FileRefreshableDataSource<?>> DATA_SOURCES = new ArrayList<>(5);
    private static final List<Closeable> WRITERS = new ArrayList<>(1);

    /**
     * 加载规则。sentinel.enable=false 时跳过；按是否配置 Dashboard 地址分流两种模式。
     */
    public static void loadRules(Prop prop) {
        if (loaded) {
            return;
        }
        String enable = prop.get(KEY_ENABLE);
        if (enable != null && "false".equalsIgnoreCase(enable.trim())) {
            return;
        }
        String file = prop.get(KEY_RULES_FILE);
        if (file == null || file.trim().isEmpty()) {
            file = "sentinel-rules.json";
        }
        file = file.trim();
        Path path = Paths.get(file);

        String dashboard = trimToEmpty(prop.get(KEY_DASHBOARD));
        if (dashboard.isEmpty()) {
            loadLocalMode(prop, path, file);
        } else {
            loadDashboardMode(prop, path, file, dashboard);
        }
        loaded = true;
    }

    /** 本地模式：物理文件则热读，否则按 classpath 资源一次性加载 */
    private static void loadLocalMode(Prop prop, Path path, String file) {
        if (Files.isRegularFile(path)) {
            int refreshMs = (int) parseLong(prop.get(KEY_REFRESH_MS), 3000L);
            DATA_SOURCES.addAll(SentinelRuleLoader.watch(path, refreshMs));
        } else {
            SentinelRuleLoader.load(SentinelKit.class.getClassLoader(), file);
        }
    }

    /** Dashboard 模式：注入属性 + 一次性初始加载 + 注册回写；不启热读（否则与回写回环） */
    private static void loadDashboardMode(Prop prop, Path path, String file, String dashboard) {
        if (!transportPresent()) {
            throw new IllegalStateException("已配置 " + KEY_DASHBOARD
                    + " 但未引入 sentinel-transport-simple-http，请为消费方添加该依赖");
        }
        String appName = trimToEmpty(prop.get(KEY_APP_NAME));
        if (appName.isEmpty()) {
            throw new IllegalStateException("Dashboard 模式必须配置 " + KEY_APP_NAME);
        }
        // 必须在 transport 初始化（首次 SphU.entry）之前注入，故 loadRules 须在最早时机调用
        System.setProperty("csp.sentinel.dashboard.server", dashboard);
        System.setProperty("project.name", appName);

        // 先替换为可回写的 property（替换会清空内存），再加载初始规则以恢复并持久化
        if (!"false".equalsIgnoreCase(trimToEmpty(prop.get(KEY_WRITABLE)))) {
            WRITERS.add(SentinelRuleWriter.register(path));
        }
        if (Files.isRegularFile(path)) {
            SentinelRuleLoader.load(path);
        } else if (SentinelKit.class.getClassLoader().getResource(file) != null) {
            SentinelRuleLoader.load(SentinelKit.class.getClassLoader(), file);
        }
    }

    /** 探测 transport 是否在 classpath：SDK 零硬引用，仅反射 */
    private static boolean transportPresent() {
        try {
            Class.forName(TRANSPORT_MARKER, false, SentinelKit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** 关闭数据源与回写，释放资源。应用关闭时调用 */
    public static void shutdown() {
        SentinelRuleLoader.closeQuietly(DATA_SOURCES);
        DATA_SOURCES.clear();
        for (Closeable writer : WRITERS) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // 关闭失败不影响主流程
            }
        }
        WRITERS.clear();
        loaded = false;
    }

    private static String trimToEmpty(String str) {
        return str == null ? "" : str.trim();
    }

    private static long parseLong(String str, long defaultValue) {
        if (str == null || str.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private SentinelKit() {}
}
