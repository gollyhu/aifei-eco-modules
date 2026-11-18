/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.spi.AuditActorProvider;
import cn.hg.aifei.audit.spi.AuditResultJudge;
import cn.hg.aifei.audit.spi.AuditSink;
import java.util.function.Supplier;

/**
 * 运行期不可变快照（非承诺 API）：一次 {@code start()} 的全部生效配置与契约。
 */
public final class AuditRuntime {

    private final AuditConfig config;

    private final AuditSink sink;

    private final AuditActorProvider actorProvider;

    private final AuditResultJudge judge;

    private final Supplier<String> traceIdSupplier;

    private final Supplier<String> clientSupplier;

    /**
     * @param config          配置
     * @param sink            落点，可为 null（已启用但未装配）
     * @param actorProvider   操作人来源，可为 null
     * @param judge           结果判定，可为 null
     * @param traceIdSupplier 链路取值器，可为 null
     * @param clientSupplier  客户端取值器，可为 null
     */
    public AuditRuntime(AuditConfig config, AuditSink sink, AuditActorProvider actorProvider,
                        AuditResultJudge judge, Supplier<String> traceIdSupplier,
                        Supplier<String> clientSupplier) {
        this.config = config;
        this.sink = sink;
        this.actorProvider = actorProvider;
        this.judge = judge;
        this.traceIdSupplier = traceIdSupplier;
        this.clientSupplier = clientSupplier;
    }

    public AuditConfig config() {
        return config;
    }

    public AuditSink sink() {
        return sink;
    }

    public AuditActorProvider actorProvider() {
        return actorProvider;
    }

    public AuditResultJudge judge() {
        return judge;
    }

    public Supplier<String> traceIdSupplier() {
        return traceIdSupplier;
    }

    public Supplier<String> clientSupplier() {
        return clientSupplier;
    }
}
