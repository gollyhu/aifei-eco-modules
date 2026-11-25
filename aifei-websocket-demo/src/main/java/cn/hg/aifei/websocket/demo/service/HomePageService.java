/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.demo.service;

import cn.aifei.core.Path;

/** WebSocket 演示主页：把 {@code /ws/demo/page} 渲染到演示主页面。 */
@Path("/ws/demo/page")
public class HomePageService {
    public String index() {
        return "/ws/index.html";
    }
}
