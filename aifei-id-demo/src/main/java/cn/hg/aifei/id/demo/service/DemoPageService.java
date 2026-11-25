/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.id.demo.service;

import cn.aifei.core.Path;

/**
 * 根路径演示页入口。
 */
@Path("/")
public class DemoPageService {

    /**
     * 渲染演示主页，与 {@code /id/demo/page} 共用模板。
     */
    public String index() {
        return "/id/index.html";
    }

}
