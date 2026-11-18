/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.service;

import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.demo.common.arch.Out;
import cn.dev33.satoken.stp.StpUtil;
import cn.hg.aifei.audit.AuditKit;
import cn.hg.aifei.audit.demo.biz.AuditedOrderService;
import cn.hg.aifei.audit.demo.common.DemoAppConfig;
import cn.hg.aifei.audit.demo.feed.EventFeed;
import cn.hg.aifei.audit.demo.feed.SinkCounters;
import cn.hg.aifei.audit.demo.sink.SwitchableAuditSink;
import cn.hg.aifei.trace.TraceKit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * demo 端点：页面状态、事件增量、手动埋点、落点切换、登录态、重置。
 *
 * <p>本类所在包<b>不挂</b>审计拦截器：页面自身的查询请求不得产事件、不得干扰计数。
 * 所有端点均为 GET，成功与预期失败都返回 {@code code=0}，异常细节放在 data 里供页面渲染。
 * 本包不是被演示库的一部分，故此处允许直接读 demo 自己的展示数据。</p>
 */
@Path("/audit/demo")
public class AuditDemoService {

    /**
     * @return 单页页面模板
     */
    @Path("page")
    public String page() {
        return "/audit/index.html";
    }

    /**
     * @return 落点、配置、计数、链路标识、当前操作人快照
     */
    @Path("state")
    public Out state() {
        SwitchableAuditSink sink = SwitchableAuditSink.get();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("sink", sink == null ? null : sink.current());
        data.put("sinks", SwitchableAuditSink.names());
        data.put("config", configView());
        data.put("counters", SinkCounters.get().snapshot());
        data.put("sinkFailMode", sink != null && sink.failMode());
        data.put("traceId", TraceKit.getTraceId());
        data.put("actor", actor());
        return Out.ok().data(data);
    }

    /**
     * @param since 上次读取到的序号
     * @return 当前序号 + 增量事件
     */
    @Path("events")
    public Out events(@Para(name = "since") String since) {
        long from = parseLong(since, 0L);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("seq", EventFeed.get().seq());
        data.put("events", EventFeed.get().since(from));
        return Out.ok().data(data);
    }

    /**
     * 场景 4：切换落点。
     *
     * @param name 落点名（memory / console / mq）
     * @return 切换结果；名字非法时 {@code code=400}
     */
    @Path("sink/switch")
    public Out switchSink(@Para(name = "name") String name) {
        SwitchableAuditSink sink = SwitchableAuditSink.get();
        if (sink == null || !sink.switchTo(name)) {
            return fail(400, "未知落点: " + name);
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("sink", sink.current());
        return Out.ok().data(data);
    }

    /**
     * 场景 8：打开 / 关闭「落点抛异常」。
     *
     * @param on true 表示打开
     * @return 当前开关状态
     */
    @Path("sink/fail")
    public Out sinkFail(@Para(name = "on") String on) {
        SwitchableAuditSink sink = SwitchableAuditSink.get();
        if (sink == null) {
            return fail(400, "落点尚未装配");
        }
        sink.failMode(parseBoolean(on, true));
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("sinkFailMode", sink.failMode());
        return Out.ok().data(data);
    }

    /**
     * 场景 2：手动埋点（不经注解，循环产多条事件）。
     *
     * @param times 次数（1~50）
     * @return 实际记录条数
     */
    @Path("manual")
    public Out manual(@Para(name = "times") String times) {
        int count = clamp(parseInt(times, 1), 1, 50);
        for (int i = 0; i < count; i++) {
            AuditKit.record("order.batch", "批量任务项", "item-" + (i + 1));
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("recorded", count);
        return Out.ok().data(data);
    }

    /**
     * 场景 5 证据：业务异常实例与冒泡实例是否同一。
     *
     * @return 异常类型、消息、同一实例判定、最近一条失败事件
     */
    @Path("failure/evidence")
    public Out failureEvidence() {
        IllegalStateException original = AuditedOrderService.thrownByBiz();
        IllegalStateException bubble = AuditedOrderService.propagated();
        Map<String, Object> thrown = new LinkedHashMap<String, Object>();
        thrown.put("type", bubble == null ? null : bubble.getClass().getName());
        thrown.put("message", bubble == null ? null : bubble.getMessage());
        thrown.put("sameInstance", original != null && original == bubble);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("thrown", thrown);
        data.put("lastFailure", EventFeed.get().lastFailure());
        return Out.ok().data(data);
    }

    /**
     * 登录为指定用户（操作人来源）。
     *
     * @param userId alice / bob
     * @return 当前操作人
     */
    @Path("login")
    public Out login(@Para(name = "userId") String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return fail(400, "userId 不能为空");
        }
        StpUtil.login(userId.trim());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("actor", actor());
        // 桥接层把 token 写在响应头；页面自行保存并在后续请求回传同名头
        data.put("tokenName", DemoAppConfig.prop().get("sa-token.token-name", "audit-demo-token"));
        data.put("token", StpUtil.getTokenValue());
        return Out.ok().data(data);
    }

    /**
     * @return 退出后无操作人
     */
    @Path("logout")
    public Out logout() {
        StpUtil.logout();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("actor", null);
        return Out.ok().data(data);
    }

    /**
     * 场景 7：清空展示数据并重置计数。
     *
     * @return 归零后的序号
     */
    @Path("reset")
    public Out reset() {
        EventFeed.get().reset();
        SinkCounters.get().reset();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("seq", EventFeed.get().seq());
        return Out.ok().data(data);
    }

    private static Map<String, Object> configView() {
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        config.put("argsEnabled", parseBoolean(DemoAppConfig.prop().get("audit.args.enabled", "false"), false));
        config.put("maxLength", parseInt(DemoAppConfig.prop().get("audit.args.max-length", "512"), 512));
        config.put("failFast", parseBoolean(DemoAppConfig.prop().get("audit.fail-fast", "false"), false));
        config.put("clientEnabled", parseBoolean(DemoAppConfig.prop().get("audit.client.enabled", "false"), false));
        config.put("module", DemoAppConfig.prop().get("audit.module", null));
        return config;
    }

    private static Map<String, Object> actor() {
        String id;
        try {
            id = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : null;
        } catch (RuntimeException e) {
            id = null;
        }
        if (id == null) {
            return null;
        }
        Map<String, Object> actor = new LinkedHashMap<String, Object>();
        actor.put("id", id);
        actor.put("name", "alice".equalsIgnoreCase(id) ? "Alice" : "bob".equalsIgnoreCase(id) ? "Bob" : id);
        return actor;
    }

    private static Out fail(int code, String msg) {
        Out out = Out.of();
        out.setCode(code);
        out.setMsg(msg);
        return out;
    }

    private static int parseInt(String value, int defaultValue) {
        try {
            return value == null || value.trim().isEmpty() ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(String value, long defaultValue) {
        try {
            return value == null || value.trim().isEmpty() ? defaultValue : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }
}
