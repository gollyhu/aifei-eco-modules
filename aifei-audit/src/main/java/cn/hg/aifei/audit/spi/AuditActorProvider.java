/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.spi;

/**
 * 操作人提供者。返回 null 表达「无操作人」（此时 {@link #actorName()} 不会被调用）。
 */
public interface AuditActorProvider {

    /**
     * @return 操作人 id；null 或空白表示无操作人
     */
    String actorId();

    /**
     * @return 操作人可读名称；可为 null
     */
    String actorName();
}
