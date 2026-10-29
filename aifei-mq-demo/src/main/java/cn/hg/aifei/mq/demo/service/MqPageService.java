/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.service;

import cn.aifei.core.Path;

/**
 * 实例入口页视图：类级路径为根 {@code "/"}，方法名 {@code index} 命中内核的默认入口约定，故
 * {@code GET /} 即本方法。类内只允许这一个 public 方法——其它 public 方法会被各自暴露成根级路由。
 */
@Path("/")
public class MqPageService {

    /**
     * 入口页视图。
     *
     * @return 模板名；模板根为 {@code webapp/view}，故必须以 {@code /} 开头
     */
    public String index() {
        return "/mq/index.html";
    }
}
