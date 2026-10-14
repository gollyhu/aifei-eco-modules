/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.websocket.demo.service;

import cn.aifei.aop.Inject;
import cn.aifei.core.Para;
import cn.aifei.core.Path;
import cn.aifei.websocket.ConnectionState;
import cn.aifei.websocket.WebSocketClient;
import cn.aifei.websocket.WebSocketServer;
import cn.aifei.websocket.WebSocketSession;
import cn.aifei.websocket.demo.common.arch.Out;
import cn.aifei.websocket.demo.ws.DemoEventLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示 Service：现场演示 aifei-websocket 的建连、收发、心跳与断线自动重连。
 *
 * <p>所有端点均为 GET，返回 {@link Out}（HTTP 恒 200，业务状态由 {@code code} 承载）。客户端与服务端实例都由
 * 装配入口（{@code WebSocketClientPlugin} / {@code WebSocketServerPlugin}）注册，此处按接口类型 {@code @Inject}
 * 取用，也就是本模块要演示的标准用法。事件明细（收到什么消息、状态怎么变、何时重连）来自
 * {@link DemoEventLog}，由两个监听器在回调线程上写入。</p>
 */
@Path("/ws/demo")
public class WebSocketDemoService {

    /**
     * 装配入口注册的客户端单例。
     */
    @Inject
    WebSocketClient client;

    /**
     * 装配入口注册的服务端单例。
     */
    @Inject
    WebSocketServer server;

    /**
     * 返回客户端状态、服务端状态、会话清单与事件日志，是现场验证的主入口。
     *
     * @return 演示现场快照
     */
    @Path("state")
    public Out state() {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        return Out.ok().data(stateMap());
    }

    /**
     * 只返回事件日志（最新在前），便于脚本单独抓取日志。
     *
     * @return 事件日志
     */
    @Path("log")
    public Out log() {
        return Out.ok().data(DemoEventLog.snapshot());
    }

    /**
     * 清空事件日志，便于演示前重置现场。
     *
     * @return 操作结果
     */
    @Path("log/clear")
    public Out clearLog() {
        DemoEventLog.clear();
        return Out.ok("事件日志已清空");
    }

    /**
     * 通过**客户端**发送一条文本；服务端监听器收到后回声回同一会话，客户端监听器把回声写入日志，
     * 从而演示「客户端 → 服务端 → 客户端」的完整闭环。
     *
     * @param text 待发送文本；缺省为 {@code hello}
     * @return 操作结果；客户端未连接时返回失败
     */
    @Path("echo/send")
    public Out echo(@Para(name = "text", defaultValue = "hello") String text) {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        if (!client.isOpen()) {
            return Out.fail("客户端当前未连接（state=" + client.state()
                    + "），无法发送；请先等待自动重连或调用 /ws/demo/client/connect");
        }
        DemoEventLog.record("DEMO_CLIENT_SEND", "通过客户端发送: " + text);
        try {
            client.send(text);
        } catch (RuntimeException e) {
            return Out.fail("客户端发送失败: " + describe(e));
        }
        return Out.ok("已通过客户端发送: " + text);
    }

    /**
     * 通过**服务端**向全部会话广播一条文本；客户端监听器把收到的广播写入日志。
     *
     * @param text 待广播文本；缺省为 {@code notice}
     * @return 操作结果，含广播时的会话数
     */
    @Path("broadcast")
    public Out broadcast(@Para(name = "text", defaultValue = "notice") String text) {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        int sessionCount = server.sessions().size();
        DemoEventLog.record("DEMO_SERVER_BROADCAST", "服务端广播: " + text + "（会话数 " + sessionCount + "）");
        try {
            server.broadcast(text);
        } catch (RuntimeException e) {
            return Out.fail("服务端广播失败: " + describe(e));
        }
        return Out.ok("已向 " + sessionCount + " 个会话广播: " + text);
    }

    /**
     * 启动服务端（演示用：先 {@code /ws/demo/server/stop} 再调用本端点，客户端会自动重连成功）。
     *
     * @return 操作结果；已启动时返回失败
     */
    @Path("server/start")
    public Out startServer() {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        if (server.isStarted()) {
            return Out.fail("服务端已启动，端口 " + server.port());
        }
        try {
            server.start();
        } catch (RuntimeException e) {
            return Out.fail("服务端启动失败: " + describe(e));
        }
        return Out.ok("服务端已启动，端口 " + server.port());
    }

    /**
     * 停止服务端，用于演示断线：服务端停掉后客户端会触发 {@code onClose}，并按指数退避自动重连。
     *
     * @return 操作结果；未启动时返回失败
     */
    @Path("server/stop")
    public Out stopServer() {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        if (!server.isStarted()) {
            return Out.fail("服务端未启动，无需停止");
        }
        try {
            server.stop();
        } catch (RuntimeException e) {
            return Out.fail("服务端停止失败: " + describe(e));
        }
        return Out.ok("服务端已停止；客户端将触发 onClose 并按指数退避自动重连");
    }

    /**
     * 手动发起客户端建连；仅当客户端处于 {@code CLOSED} 时允许。
     *
     * @return 操作结果；状态不是 {@code CLOSED} 时返回失败
     */
    @Path("client/connect")
    public Out connectClient() {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        if (client.state() != ConnectionState.CLOSED) {
            return Out.fail("客户端当前状态为 " + client.state() + "，仅 CLOSED 时允许 connect()");
        }
        try {
            client.connect();
        } catch (RuntimeException e) {
            return Out.fail("客户端建连失败: " + describe(e));
        }
        return Out.ok("已发起建连（异步），请稍后查看 /ws/demo/state");
    }

    /**
     * 手动关闭客户端；关闭后**不再自动重连**，直到再次调用 {@code /ws/demo/client/connect}。
     *
     * @return 操作结果
     */
    @Path("client/disconnect")
    public Out disconnectClient() {
        Out missing = requireInjected();
        if (missing != null) {
            return missing;
        }
        try {
            client.close();
        } catch (RuntimeException e) {
            return Out.fail("客户端关闭失败: " + describe(e));
        }
        return Out.ok("已请求关闭客户端（close() 之后不再自动重连）");
    }

    /**
     * 组装现场快照：客户端状态、服务端状态与会话清单、事件日志。
     *
     * @return 现场快照，字段含义见方法内注释
     */
    private Map<String, Object> stateMap() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("clientState", client.state().name());
        data.put("clientOpen", client.isOpen());
        data.put("clientRemoteAddress", client.remoteAddress());
        data.put("clientBackendType", client.backendType());

        data.put("serverStarted", server.isStarted());
        data.put("serverPort", server.port());
        data.put("serverBackendType", server.backendType());

        Collection<WebSocketSession> sessions = server.sessions();
        data.put("serverSessionCount", sessions.size());
        List<Map<String, Object>> sessionList = new ArrayList<Map<String, Object>>(sessions.size());
        for (WebSocketSession session : sessions) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", session.id());
            item.put("state", session.state().name());
            item.put("remoteAddress", session.remoteAddress());
            sessionList.add(item);
        }
        data.put("serverSessions", sessionList);

        data.put("messageLogSize", DemoEventLog.size());
        data.put("messageLog", DemoEventLog.snapshot());
        return data;
    }

    /**
     * 校验两个注入字段都已就绪；未就绪时返回失败响应，避免抛出空指针。
     *
     * @return 就绪时返回 {@code null}，否则返回失败响应
     */
    private Out requireInjected() {
        if (client == null || server == null) {
            return Out.fail("WebSocket 实例尚未装配完成（client=" + client + ", server=" + server
                    + "），请确认 config(Plugins) 已注册两个 WebSocket 插件");
        }
        return null;
    }

    /**
     * 把运行态异常整理成一行可读描述，便于端点响应中定位问题。
     *
     * @param e 捕获到的异常
     * @return 形如 {@code IllegalStateException: xxx} 的描述
     */
    private String describe(RuntimeException e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
