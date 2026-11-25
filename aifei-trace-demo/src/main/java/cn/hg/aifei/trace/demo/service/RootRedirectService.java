/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.trace.demo.service;

import cn.aifei.core.Path;

/**
 * 根路径入口：把 {@code /} 引导至演示主页面 {@code /trace/demo/page}。
 *
 * <p>沿用 demo 先例：类级 {@code @Path("/")} + 入口方法名 {@code index}（方法不加 {@code @Path}），
 * 框架取空 methodPath 注册为根路径。</p>
 */
@Path("/")
public class RootRedirectService {

    /**
     * 根路径跳转页：IoHandler 仅支持 view / json 两种输出，跳转由 view 内的 meta refresh 完成。
     *
     * @return 跳转页模板名（绝对路径）
     */
    public String index() {
        return "/demo/redirect.html";
    }
}
