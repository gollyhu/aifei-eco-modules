/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.actor;

import cn.dev33.satoken.stp.StpUtil;
import cn.hg.aifei.audit.spi.AuditActorProvider;

/**
 * 操作人来源：从 Sa-Token 当前登录态取（示例工程行为，模块本身零 sa-token 依赖）。
 */
public final class SaTokenActorProvider implements AuditActorProvider {

    @Override
    public String actorId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String actorName() {
        String id = actorId();
        if (id == null) {
            return null;
        }
        if ("alice".equalsIgnoreCase(id)) {
            return "Alice";
        }
        if ("bob".equalsIgnoreCase(id)) {
            return "Bob";
        }
        return id;
    }
}
