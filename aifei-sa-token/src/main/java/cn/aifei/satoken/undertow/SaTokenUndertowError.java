/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.undertow;

import cn.aifei.satoken.spi.SaTokenErrorHandler;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

/**
 * Undertow 场景下的鉴权失败策略。
 *
 * <p>借用桥接层持有的 {@link HttpServerExchange} 直接调整 HTTP 状态码，
 * 响应体仍交由消费方 IoHandler 统一输出，因此不需要结束 exchange。</p>
 */
public final class SaTokenUndertowError {

    /**
     * 默认策略：未登录置 401，其余鉴权失败置 403。
     *
     * <p>两个拦截器的无参构造均采用本策略。需要业务码语义（HTTP 恒 200）时，
     * 传入自定义 {@link SaTokenErrorHandler} 返回消费方自己的 Output 即可。</p>
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
