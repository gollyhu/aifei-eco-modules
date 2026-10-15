/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.demo.service;

import cn.aifei.core.Path;

/**
 * 演示 Demo：Web 演示 aifei-websocket
 */
@Path("/")
public class DemoPageService {

    /**
     * 演示页
     */
    public String index() {
        return "ws/index.html";
    }

}
