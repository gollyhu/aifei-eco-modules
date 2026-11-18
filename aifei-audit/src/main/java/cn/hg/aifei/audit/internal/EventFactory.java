/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.AuditEvent;

/**
 * 事件构造唯一实现点（非承诺 API）：现场值 + 结果 + 错误信息 → 不可变事件。
 */
public final class EventFactory {

    private EventFactory() {
    }

    /**
     * @param ctx          采集现场值
     * @param result       结果
     * @param errorCode    错误码，可空
     * @param errorMessage 错误消息，可空
     * @return 已构造完成的事件
     */
    public static AuditEvent of(AuditContext ctx, AuditEvent.Result result,
                                String errorCode, String errorMessage) {
        return new AuditEvent(EventIds.next(), ctx.getOccurredAt(), ctx.getAction(), ctx.getModule(),
                ctx.getDesc(), ctx.getActorId(), ctx.getActorName(), ctx.getTarget(), ctx.getArgsSummary(),
                result, errorCode, errorMessage, ctx.getElapsedMillis(), ctx.getTraceId(), ctx.getClient());
    }
}
