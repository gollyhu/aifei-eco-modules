/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.service;

import cn.aifei.core.Path;

/** 治理演示主页：把 {@code /demo/page} 渲染到演示主页面。 */
@Path("/demo/page")
public class HomePageService {
    public String index() {
        return "/sentinel/index.html";
    }
}
