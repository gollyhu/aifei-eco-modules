/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.sentinel.demo.service;

import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.aifei.util.Prop;
import cn.hg.aifei.sentinel.demo.common.DemoAppConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示三种治理能力：index 流控、slow 熔断、detail 热点参数；另提供页面用的运行态端点 state。
 *
 * <p>state 的资源名无对应规则，不会被引擎限流或熔断，页面进入时靠它自动识别运行模式。</p>
 */
@Path("/demo")
public class DemoService {

    // 方法名 index 命中内核默认入口约定会映射为 /demo，显式声明让 /demo/index 可达
    @Path("index")
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

    /**
     * 页面用运行态快照：模式、规则文件、Dashboard 地址与入口提示。
     *
     * @return 运行态快照；{@code data.mode} 为 {@code local} 或 {@code dashboard}
     */
    @Path("state")
    public Out state() {
        Prop p = DemoAppConfig.prop();
        boolean dashboard = DemoAppConfig.isDashboardMode(p);
        String server = dashboard ? p.get(DemoAppConfig.DASHBOARD_SERVER_KEY).trim() : "";
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("mode", dashboard ? "dashboard" : "local");
        data.put("modeLabel", dashboard ? "Dashboard 模式" : "本地模式");
        data.put("rulesFile", p == null ? DemoAppConfig.DEFAULT_RULES_FILE
                : p.get(DemoAppConfig.RULES_FILE_KEY, DemoAppConfig.DEFAULT_RULES_FILE));
        data.put("dashboardServer", server);
        data.put("dashboardUrl", dashboard ? "http://" + server : "");
        data.put("configName", System.getProperty(DemoAppConfig.CONFIG_KEY, DemoAppConfig.DEFAULT_CONFIG));
        data.put("tip", dashboard
                ? "规则由 Sentinel 控制台推送，SDK 自动回写 sentinel-rules.json，重启不丢。"
                : "规则来自 sentinel-rules.json 物理文件，修改后秒级热生效，无需重启。");
        return Out.ok().data(data);
    }
}
