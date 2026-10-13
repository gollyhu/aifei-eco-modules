/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.satoken.spi;

/**
 * 路径命中后执行的鉴权动作。
 *
 * <p>典型实现为调用 Sa-Token 的静态门面，如
 * {@code () -> StpUtil.checkLogin()} 或 {@code () -> StpUtil.checkRole("admin")}。</p>
 *
 * <p>不直接复用 Sa-Token 的函数类型，避免把其函数接口暴露进消费方装配代码。</p>
 */
@FunctionalInterface
public interface SaAuthStrategy {

    /**
     * 执行鉴权。通过则正常返回，失败则抛出异常。
     *
     * @throws Exception 鉴权失败，或鉴权过程中产生的其他异常
     */
    void check() throws Exception;
}
