/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.aifei;

import cn.aifei.aop.Invocation;
import cn.aifei.satoken.spi.SaTokenErrorHandler;
import cn.dev33.satoken.exception.BackResultException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.exception.StopMatchException;

/**
 * Sa-Token 异常到 aifei 返回值的转换，唯一定义点。
 *
 * <p>供路径鉴权与注解鉴权两个拦截器共用，避免各自维护一套近似分支。</p>
 */
final class SaTokenErrorSupport {

    /**
     * 尝试把异常转换为 action 的返回值。
     *
     * @param inv 当前调用上下文
     * @param e 捕获到的异常
     * @param errorHandler 鉴权失败策略
     * @return true 表示异常已转换完毕，调用方无需再抛出；false 表示不归本类处理
     */
    static boolean handle(Invocation inv, Throwable e, SaTokenErrorHandler errorHandler) {
        if (e instanceof StopMatchException) {
            return false;
        }
        if (e instanceof BackResultException) {
            inv.setReturnValue(((BackResultException) e).result);
            return true;
        }
        if (e instanceof SaTokenException) {
            // 策略的返回值直接作为 action 返回值，交消费方 IoHandler 渲染；策略自身抛出的异常照常向上传播
            inv.setReturnValue(errorHandler.handle((SaTokenException) e));
            return true;
        }
        return false;
    }

    private SaTokenErrorSupport() {}
}
