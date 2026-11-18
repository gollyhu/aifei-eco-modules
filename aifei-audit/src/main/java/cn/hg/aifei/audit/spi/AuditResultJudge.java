/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.spi;

/**
 * 业务码失败判定：只放宽「正常返回也可能是失败」这一种情况，不改异常语义。
 */
public interface AuditResultJudge {

    /**
     * @param action      动作码
     * @param returnValue 业务方法返回值（可能为 null）
     * @return 判定为失败时 {@code true}
     */
    boolean isFailure(String action, Object returnValue);
}
