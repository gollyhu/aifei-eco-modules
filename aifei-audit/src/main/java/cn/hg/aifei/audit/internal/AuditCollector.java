/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.AuditEvent;
import java.util.function.Supplier;

/**
 * 采集编排唯一入口（非承诺 API）：注解路径与手动埋点共用同一事件构造、同一落点、同一隔离策略。
 *
 * <p>注解路径只认 {@link AuditInvocation} 端口（框架调用现场由接入层适配），入参只能在
 * {@code proceed()} 之后读取，故采集全部落在 {@code finally}；业务异常先记录再原样上抛。</p>
 */
public final class AuditCollector {

    private AuditCollector() {
    }

    /**
     * 注解路径。
     *
     * @param inv 调用现场端口
     * @throws Exception 业务方法原样抛出的异常（或 fail-fast 下的落点异常）
     */
    public static void intercept(AuditInvocation inv) throws Exception {
        final AuditAnnotations.ActionSpec spec = AuditAnnotations.of(inv.method());
        final AuditRuntime rt = AuditHolder.snapshot();
        if (spec == null || rt == null || rt.sink() == null) {
            inv.proceed();
            return;
        }

        final long startNanos = Clock.startNanos();
        final long occurredAt = Clock.epochMillis();
        final Throwable[] failure = new Throwable[1];
        try {
            inv.proceed();
        } catch (Throwable t) {
            failure[0] = t;
            throw t;
        } finally {
            final AuditInvocation invocation = inv;
            AuditEvent event = SafeInvoker.capture(new Supplier<AuditEvent>() {
                @Override
                public AuditEvent get() {
                    return build(rt, spec, invocation, occurredAt, startNanos, failure[0]);
                }
            });
            if (event != null) {
                SafeInvoker.deliver(rt, event, failure[0] != null);
            }
        }
    }

    /**
     * 手动埋点路径：入参摘要恒空、耗时恒 {@code 0}、目标对象只能由调用方给出。
     *
     * @param action       动作码
     * @param desc         描述
     * @param target       目标对象标识
     * @param result       结果
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @throws cn.hg.aifei.audit.AuditException 插件未启动或已停止
     */
    public static void manual(String action, String desc, String target, AuditEvent.Result result,
                              String errorCode, String errorMessage) {
        final AuditRuntime rt = AuditHolder.snapshot();
        if (rt == null) {
            throw AuditErrors.notStarted();
        }
        if (rt.sink() == null) {
            return;
        }
        final String actionCode = action;
        final String description = desc;
        final String targetId = target;
        final AuditEvent.Result finalResult = result;
        final String code = errorCode;
        final String message = errorMessage;
        AuditEvent event = SafeInvoker.capture(new Supplier<AuditEvent>() {
            @Override
            public AuditEvent get() {
                return buildManual(rt, actionCode, description, targetId, finalResult, code, message);
            }
        });
        if (event != null) {
            SafeInvoker.deliver(rt, event, false);
        }
    }

    private static AuditEvent build(AuditRuntime rt, AuditAnnotations.ActionSpec spec, AuditInvocation inv,
                                    long occurredAt, long startNanos, Throwable businessFailure) {
        AuditConfig config = rt.config();
        String action = spec.getAction();
        String actorId = SafeInvoker.actorId(rt);
        String actorName = actorId == null ? null : SafeInvoker.actorName(rt);
        String argsSummary = config.argsEnabled() || spec.isArgs()
                ? Truncator.summary(inv.args(), spec.getMask(), config.maxLength())
                : null;
        AuditEvent.Result result = ResultPolicy.resolve(businessFailure, inv.returnValue(), action, rt.judge());
        AuditContext ctx = new AuditContext(action, spec.getDesc(), config.module(), null, argsSummary,
                occurredAt, Clock.elapsedMillis(startNanos), actorId, actorName,
                SafeInvoker.supply(rt.traceIdSupplier()),
                config.clientEnabled() ? SafeInvoker.supply(rt.clientSupplier()) : null);
        String errorMessage = businessFailure == null ? null : businessFailure.getMessage();
        return EventFactory.of(ctx, result, ResultPolicy.errorCode(businessFailure, result), errorMessage);
    }

    private static AuditEvent buildManual(AuditRuntime rt, String action, String desc, String target,
                                          AuditEvent.Result result, String errorCode, String errorMessage) {
        AuditConfig config = rt.config();
        String actorId = SafeInvoker.actorId(rt);
        String actorName = actorId == null ? null : SafeInvoker.actorName(rt);
        AuditContext ctx = new AuditContext(action, desc, config.module(), target, null,
                Clock.epochMillis(), 0L, actorId, actorName,
                SafeInvoker.supply(rt.traceIdSupplier()),
                config.clientEnabled() ? SafeInvoker.supply(rt.clientSupplier()) : null);
        return EventFactory.of(ctx, result, errorCode, errorMessage);
    }
}
