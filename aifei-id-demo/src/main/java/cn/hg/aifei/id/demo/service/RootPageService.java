/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo.service;

import cn.aifei.core.Path;

/** 根路径入口：把 {@code /} 引导至演示主页面 {@code /id/demo/page}。 */
@Path("/")
public class RootPageService {
    public String index() {
        return "/demo/redirect.html";
    }
}
