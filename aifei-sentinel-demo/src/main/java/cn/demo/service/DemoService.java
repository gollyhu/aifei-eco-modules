/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.demo.service;

import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.demo.arch.Out;

/** 演示三种治理能力：index 流控、slow 熔断、detail 热点参数 */
@Path("/demo")
public class DemoService {

    public Out index() {
        return Out.ok("index 正常响应");
    }

    public Out slow() {
        try {
            Thread.sleep(800);   // 模拟慢接口，配合熔断规则（RT > 500ms）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Out.ok("slow 正常响应");
    }

    public Out detail(@Para(name="id", defaultValue="0") int id) {
        return Out.ok("detail-" + id);
    }
}
