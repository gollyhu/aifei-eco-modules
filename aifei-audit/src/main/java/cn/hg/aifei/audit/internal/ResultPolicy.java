/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.spi.AuditResultJudge;

/**
 * 成败判定兜底唯一实现点（非承诺 API）：业务异常即失败，其次由可选判定放宽「正常返回也算失败」。
 */
public final class ResultPolicy {

    /**
     * 业务码失败（判定实现判为失败但业务未抛异常）时事件上的固定错误码。
     */
    public static final String BUSINESS_CODE_FAILURE = "BUSINESS_CODE_FAILURE";

    private ResultPolicy() {
    }

    /**
     * @param businessFailure 业务异常，无异常为 null
     * @param returnValue     业务返回值
     * @param action          动作码
     * @param judge           可选判定实现，可为 null
     * @return 结果
     */
    public static AuditEvent.Result resolve(Throwable businessFailure, Object returnValue,
                                            String action, AuditResultJudge judge) {
        if (businessFailure != null) {
            return AuditEvent.Result.FAILURE;
        }
        if (judge != null && SafeInvoker.judge(judge, action, returnValue)) {
            return AuditEvent.Result.FAILURE;
        }
        return AuditEvent.Result.SUCCESS;
    }

    /**
     * @param businessFailure 业务异常，无异常为 null
     * @param result          已判定结果
     * @return 错误码：异常类全名 / 业务码失败常量 / {@code null}
     */
    public static String errorCode(Throwable businessFailure, AuditEvent.Result result) {
        if (businessFailure != null) {
            return businessFailure.getClass().getName();
        }
        return result.isFailure() ? BUSINESS_CODE_FAILURE : null;
    }
}
