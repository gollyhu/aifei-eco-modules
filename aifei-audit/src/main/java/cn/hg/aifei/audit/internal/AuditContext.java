/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

/**
 * 采集现场值对象（非承诺 API）：只承载已取值字段，构造完成即不可变。
 */
public final class AuditContext {

    private final String action;

    private final String desc;

    private final String module;

    private final String target;

    private final String argsSummary;

    private final long occurredAt;

    private final long elapsedMillis;

    private final String actorId;

    private final String actorName;

    private final String traceId;

    private final String client;

    /**
     * @param action        动作码
     * @param desc          描述；空白归一为 {@code null}
     * @param module        业务模块
     * @param target        目标对象标识
     * @param argsSummary   入参摘要
     * @param occurredAt    发生时刻
     * @param elapsedMillis 耗时毫秒
     * @param actorId       操作人 id
     * @param actorName     操作人名
     * @param traceId       链路标识
     * @param client        客户端标识
     */
    public AuditContext(String action, String desc, String module, String target, String argsSummary,
                        long occurredAt, long elapsedMillis, String actorId, String actorName,
                        String traceId, String client) {
        this.action = action;
        this.desc = desc == null || desc.trim().isEmpty() ? null : desc;
        this.module = module;
        this.target = target;
        this.argsSummary = argsSummary;
        this.occurredAt = occurredAt;
        this.elapsedMillis = elapsedMillis;
        this.actorId = actorId;
        this.actorName = actorName;
        this.traceId = traceId;
        this.client = client;
    }

    public String getAction() {
        return action;
    }

    public String getDesc() {
        return desc;
    }

    public String getModule() {
        return module;
    }

    public String getTarget() {
        return target;
    }

    public String getArgsSummary() {
        return argsSummary;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getClient() {
        return client;
    }
}
