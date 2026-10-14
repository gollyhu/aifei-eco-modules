/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.satoken.undertow;

import cn.hg.aifei.satoken.spi.SaTokenErrorHandler;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

/**
 * Undertow 场景下的鉴权失败策略。
 */
public final class SaTokenUndertowError {

    /**
     * 默认策略：未登录置 401，其余鉴权失败置 403。
     *
     * @return 鉴权失败策略
     */
    public static SaTokenErrorHandler defaultHandler() {
        return e -> {
            HttpServerExchange exchange = SaTokenUndertowContext.getExchange();
            if (exchange != null) {
                exchange.setStatusCode(statusOf(e));
            }
            return null;
        };
    }

    /** 未登录 → 401；无角色、无权限、被顶下线等 → 403 */
    private static int statusOf(SaTokenException e) {
        return e instanceof NotLoginException ? StatusCodes.UNAUTHORIZED : StatusCodes.FORBIDDEN;
    }

    private SaTokenUndertowError() {}
}
