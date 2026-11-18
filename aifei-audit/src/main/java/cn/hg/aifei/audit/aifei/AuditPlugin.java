/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.aifei;

import cn.aifei.aop.Aop;
import cn.aifei.plugin.Plugin;
import cn.aifei.scanner.Scanner;
import cn.aifei.util.Prop;
import cn.hg.aifei.audit.internal.AuditConfig;
import cn.hg.aifei.audit.internal.AuditEnvironment;
import cn.hg.aifei.audit.internal.AuditHolder;
import cn.hg.aifei.audit.internal.AuditRuntime;
import cn.hg.aifei.audit.internal.AuditScanner;
import cn.hg.aifei.audit.internal.AuditSpi;
import cn.hg.aifei.audit.internal.SafeInvoker;
import cn.hg.aifei.audit.spi.AuditActorProvider;
import cn.hg.aifei.audit.spi.AuditResultJudge;
import cn.hg.aifei.audit.spi.AuditSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 装配入口（接入构件，非承诺 API）：构造期只持配置源引用，解析、校验与快照安装全部在 {@link #start()}。
 *
 * <p>启动失败不留残留态：快照安装是最后一步，之前任何失败都不会写入；模块不向容器注册任何对象。
 * {@code true} 幂等：重复 {@code start()} / {@code stop()} 无副作用。</p>
 */
public class AuditPlugin implements Plugin {

    /**
     * 框架能力端口的唯一实现：容器查找与包扫描都收敛在此，{@code internal} 因而零框架引用。
     */
    private static final AuditEnvironment AIFEI_ENVIRONMENT = new AuditEnvironment() {
        @Override
        public <T> T lookup(Class<T> type) {
            return Aop.get(type);
        }

        @Override
        public Set<Class<?>> scan(String packageName) {
            return new Scanner().scan(packageName);
        }
    };

    private final Prop prop;

    private final List<String> extraPackages = new ArrayList<String>();

    private final List<Class<?>> extraClasses = new ArrayList<Class<?>>();

    private Supplier<String> traceIdSupplier;

    private Supplier<String> clientSupplier;

    private boolean started;

    /**
     * @param prop 配置源；null 抛 {@link IllegalArgumentException}（构造期只持引用，不解析、不校验）
     */
    public AuditPlugin(Prop prop) {
        if (prop == null) {
            throw new IllegalArgumentException("prop 不能为 null");
        }
        this.prop = prop;
    }

    /**
     * 追加扫描包（{@code start()} 前调用；与配置项合并去重）。
     *
     * @param packages 包名，空白项忽略
     * @return 自身
     */
    public AuditPlugin addScanPackages(String... packages) {
        if (packages != null) {
            for (int i = 0; i < packages.length; i++) {
                if (packages[i] != null && !packages[i].trim().isEmpty()) {
                    extraPackages.add(packages[i].trim());
                }
            }
        }
        return this;
    }

    /**
     * 追加扫描类（{@code start()} 前调用；用于 jar / fat-jar 场景兜底）。
     *
     * @param classes 类，null 项忽略
     * @return 自身
     */
    public AuditPlugin addScanClasses(Class<?>... classes) {
        if (classes != null) {
            for (int i = 0; i < classes.length; i++) {
                if (classes[i] != null) {
                    extraClasses.add(classes[i]);
                }
            }
        }
        return this;
    }

    /**
     * 注入链路标识取值（如 {@code TraceKit::getTraceId}）；null 表示不采集。
     *
     * @param supplier 取值器
     * @return 自身
     */
    public AuditPlugin setTraceIdSupplier(Supplier<String> supplier) {
        this.traceIdSupplier = supplier;
        return this;
    }

    /**
     * 注入客户端标识取值；仅 {@code ...client.enabled=true} 时被调用。
     *
     * @param supplier 取值器
     * @return 自身
     */
    public AuditPlugin setClientSupplier(Supplier<String> supplier) {
        this.clientSupplier = supplier;
        return this;
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        AuditConfig config = AuditConfig.parse(prop.getProperties());
        AuditScanner.validate(config, extraPackages, extraClasses, AIFEI_ENVIRONMENT);
        if (!config.enabled()) {
            started = true;
            return;
        }
        AuditSink sink = AuditSpi.sink(AIFEI_ENVIRONMENT);
        AuditActorProvider actorProvider = AuditSpi.actorProvider(AIFEI_ENVIRONMENT);
        AuditResultJudge judge = AuditSpi.judge(AIFEI_ENVIRONMENT);
        if (sink == null) {
            SafeInvoker.warn("已启用但容器内未发现 AuditSink 实现，本次启动不采集事件");
        }
        AuditHolder.install(new AuditRuntime(config, sink, actorProvider, judge,
                traceIdSupplier, clientSupplier));
        started = true;
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            return;
        }
        AuditHolder.clear();
        started = false;
    }
}
