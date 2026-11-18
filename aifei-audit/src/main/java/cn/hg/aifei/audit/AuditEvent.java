/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

/**
 * 审计事件：全字段为 {@code final} 标量，构造完成即不可变，可直接交付落点。
 *
 * <p>不覆写 {@code equals} / {@code hashCode}：事件是流水记录，无业务等价语义。</p>
 */
public final class AuditEvent {

    /**
     * 结果二值枚举；作为事件字段的承载类型，不计入根包顶层类型。
     */
    public enum Result {
        SUCCESS,
        FAILURE;

        /**
         * @return 失败为 {@code true}
         */
        public boolean isFailure() {
            return this == FAILURE;
        }
    }

    private final String eventId;

    private final long occurredAt;

    private final String action;

    private final String module;

    private final String desc;

    private final String actorId;

    private final String actorName;

    private final String target;

    private final String argsSummary;

    private final Result result;

    private final String errorCode;

    private final String errorMessage;

    private final long elapsedMillis;

    private final String traceId;

    private final String client;

    /**
     * 全参构造器。仅供模块内部（事件工厂）与测试构造，消费方不应自行创建事件。
     *
     * @param eventId       事件号
     * @param occurredAt    发生时刻（epoch 毫秒）
     * @param action        动作码
     * @param module        业务模块，可空
     * @param desc          描述，可空
     * @param actorId       操作人 id，可空
     * @param actorName     操作人名，可空
     * @param target        目标对象标识，可空
     * @param argsSummary   脱敏后的入参摘要，可空
     * @param result        结果
     * @param errorCode     错误码，可空
     * @param errorMessage  错误消息，可空
     * @param elapsedMillis 耗时毫秒
     * @param traceId       链路标识，可空
     * @param client        客户端标识，可空
     * @throws IllegalArgumentException eventId / action 为空白，或 result 为 null
     */
    public AuditEvent(String eventId, long occurredAt, String action, String module, String desc,
                      String actorId, String actorName, String target, String argsSummary,
                      Result result, String errorCode, String errorMessage, long elapsedMillis,
                      String traceId, String client) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("eventId 不能为空白");
        }
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("action 不能为空白");
        }
        if (result == null) {
            throw new IllegalArgumentException("result 不能为 null");
        }
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.action = action;
        this.module = module;
        this.desc = desc;
        this.actorId = actorId;
        this.actorName = actorName;
        this.target = target;
        this.argsSummary = argsSummary;
        this.result = result;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.elapsedMillis = elapsedMillis;
        this.traceId = traceId;
        this.client = client;
    }

    public String getEventId() {
        return eventId;
    }

    public long getOccurredAt() {
        return occurredAt;
    }

    public String getAction() {
        return action;
    }

    public String getModule() {
        return module;
    }

    public String getDesc() {
        return desc;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getTarget() {
        return target;
    }

    public String getArgsSummary() {
        return argsSummary;
    }

    public Result getResult() {
        return result;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getClient() {
        return client;
    }

    /**
     * 输出全部要素标识（含事件号与动作码），空值渲染为 {@code null}。
     *
     * @return 事件文本
     */
    @Override
    public String toString() {
        return "AuditEvent{eventId=" + eventId
                + ", occurredAt=" + occurredAt
                + ", action=" + action
                + ", module=" + module
                + ", desc=" + desc
                + ", actorId=" + actorId
                + ", actorName=" + actorName
                + ", target=" + target
                + ", argsSummary=" + argsSummary
                + ", result=" + result
                + ", errorCode=" + errorCode
                + ", errorMessage=" + errorMessage
                + ", elapsedMillis=" + elapsedMillis
                + ", traceId=" + traceId
                + ", client=" + client
                + "}";
    }
}
