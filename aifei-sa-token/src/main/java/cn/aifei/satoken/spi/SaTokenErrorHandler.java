/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.spi;

import cn.dev33.satoken.exception.SaTokenException;

/**
 * 鉴权失败时的响应策略。
 *
 * <p>解耦「SDK 不知道消费方 Output 类型」这一矛盾：返回值经
 * {@code Invocation.setReturnValue(...)} 交由消费方 IoHandler 正常渲染。</p>
 *
 * <p>位于 spi 层，不依赖 aifei 内核任何类型。</p>
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
