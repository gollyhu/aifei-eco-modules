/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

import cn.hg.aifei.audit.AuditException;

/**
 * 模块全部错误消息文本的唯一构造点（非承诺 API）。
 */
public final class AuditErrors {

    private AuditErrors() {
    }

    /**
     * 配置取值非法：消息固定为「键名 + 原值 + 规则」，便于启动期定位。
     *
     * @param key  配置键名
     * @param raw  原始取值
     * @param rule 合法规则说明
     * @return 待抛异常
     */
    public static AuditException configInvalid(String key, String raw, String rule) {
        return new AuditException(key + " 取值非法: '" + raw + "'（" + rule + "）");
    }

    /**
     * @return 未提供任何注解扫描范围时的异常
     */
    public static AuditException scanRangeMissing() {
        return new AuditException("未提供注解扫描范围：请配置 " + AuditConfig.SCAN_PACKAGES_KEY
                + " 或 " + AuditConfig.SCAN_CLASSES_KEY
                + "，或调用 AuditPlugin#addScanPackages / addScanClasses");
    }

    /**
     * @param signature 全限定类名 + {@code #} + 方法名
     * @return 注解动作码为空串时的异常
     */
    public static AuditException emptyAction(String signature) {
        return new AuditException("@Audit(action) 不能为空串: " + signature);
    }

    /**
     * @return 手动埋点时插件不可用的异常
     */
    public static AuditException notStarted() {
        return new AuditException("审计插件未启动或已停止，无法手动埋点");
    }
}
