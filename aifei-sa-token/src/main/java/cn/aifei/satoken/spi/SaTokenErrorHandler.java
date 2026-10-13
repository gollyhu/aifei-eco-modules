/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.spi;

import cn.dev33.satoken.exception.SaTokenException;

/**
 * 鉴权失败时的响应策略。
 */
@FunctionalInterface
public interface SaTokenErrorHandler {

    /**
     * 处理鉴权失败。
     *
     * @param e 鉴权失败异常，含 {@code NotLoginException} / {@code NotRoleException} 等子类
     * @return 交由消费方渲染的返回值；返回值不应为 {@code null} 以外的不可渲染类型
     */
    Object handle(SaTokenException e);
}
