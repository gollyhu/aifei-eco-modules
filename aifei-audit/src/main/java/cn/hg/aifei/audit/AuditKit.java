/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit;

import cn.hg.aifei.audit.internal.AuditCollector;

/**
 * 手动埋点门面：注解覆盖不到的位置（异步回调、批处理循环）用它产出与注解路径同形的事件。
 *
 * <p>属 Kit（有状态门面）：须先装配 plugin，未启动 / 已停止时抛 {@link AuditException}。
 * 与注解路径的差异：不携带入参摘要、耗时恒 {@code 0}、目标对象只能由调用方给出、
 * 业务异常须由调用方自行捕获后再埋失败事件。</p>
 */
public final class AuditKit {

    private AuditKit() {
    }

    /**
     * 成功事件，无目标对象。
     *
     * @param action 动作码，不得为空白
     * @param desc   描述，可空
     * @throws IllegalArgumentException action 为空白
     * @throws AuditException           插件未启动或已停止
     */
    public static void record(String action, String desc) {
        record(action, desc, null);
    }

    /**
     * 成功事件 + 目标对象标识。
     *
     * @param action 动作码，不得为空白
     * @param desc   描述，可空
     * @param target 目标对象标识，可空
     * @throws IllegalArgumentException action 为空白
     * @throws AuditException           插件未启动或已停止
     */
    public static void record(String action, String desc, String target) {
        requireAction(action);
        AuditCollector.manual(action, desc, target, AuditEvent.Result.SUCCESS, null, null);
    }

    /**
     * 失败事件，错误码取异常类全名、消息取 {@code getMessage()}。
     *
     * @param action 动作码，不得为空白
     * @param desc   描述，可空
     * @param target 目标对象标识，可空
     * @param error  业务异常，不得为 null
     * @throws IllegalArgumentException action 为空白或 error 为 null
     * @throws AuditException           插件未启动或已停止
     */
    public static void recordFailure(String action, String desc, String target, Throwable error) {
        requireAction(action);
        if (error == null) {
            throw new IllegalArgumentException("error 不能为 null");
        }
        AuditCollector.manual(action, desc, target, AuditEvent.Result.FAILURE,
                error.getClass().getName(), error.getMessage());
    }

    /**
     * 失败事件，错误码与消息由调用方给出。
     *
     * @param action       动作码，不得为空白
     * @param desc         描述，可空
     * @param target       目标对象标识，可空
     * @param errorCode    错误码，可空
     * @param errorMessage 错误消息，可空
     * @throws IllegalArgumentException action 为空白
     * @throws AuditException           插件未启动或已停止
     */
    public static void recordFailure(String action, String desc, String target,
                                     String errorCode, String errorMessage) {
        requireAction(action);
        AuditCollector.manual(action, desc, target, AuditEvent.Result.FAILURE, errorCode, errorMessage);
    }

    private static void requireAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new IllegalArgumentException("action 不能为空白");
        }
    }
}
