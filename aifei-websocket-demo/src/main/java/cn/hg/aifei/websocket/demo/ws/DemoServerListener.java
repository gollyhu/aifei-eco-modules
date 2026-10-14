/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.demo.ws;

import cn.hg.aifei.websocket.Message;
import cn.hg.aifei.websocket.ServerListener;
import cn.hg.aifei.websocket.WebSocketSession;

/**
 * 服务端监听器的演示实现：记录会话级事件，并对每条收到的文本帧**回声**回同一会话。
 *
 * <p>「回调内回声」是模块推荐的写法（回调内可以安全地再调 {@code session.send}），本类据此实现
 * 「客户端 → 服务端 → 客户端」的完整闭环演示。回调运行在底层库的工作线程上，故不做耗时阻塞操作。</p>
 */
public class DemoServerListener implements ServerListener {

    /**
     * 创建监听器；无状态，可安全地被单个服务端实例持有。
     */
    public DemoServerListener() {
    }

    /**
     * 新会话登记完成、可收发后记录一条日志。
     *
     * @param session 新会话
     */
    @Override
    public void onOpen(WebSocketSession session) {
        DemoEventLog.record("SERVER_ON_OPEN",
                "session=" + session.id() + ", remote=" + session.remoteAddress());
    }

    /**
     * 收到一条数据帧时记录一条日志，并把同一内容回声回该会话。
     *
     * @param session 来源会话
     * @param message 收到的消息；永不为 {@code null}
     */
    @Override
    public void onMessage(WebSocketSession session, Message message) {
        if (message.isText()) {
            DemoEventLog.record("SERVER_ON_MESSAGE", "session=" + session.id() + ", 文本: " + message.asText());
        } else {
            DemoEventLog.record("SERVER_ON_MESSAGE",
                    "session=" + session.id() + ", 二进制 " + message.size() + " 字节");
        }
        echo(session, message);
    }

    /**
     * 会话关闭前记录一条日志。
     *
     * @param session 被关闭的会话
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote {@code true} 表示由对端发起
     */
    @Override
    public void onClose(WebSocketSession session, int code, String reason, boolean remote) {
        DemoEventLog.record("SERVER_ON_CLOSE",
                "session=" + session.id() + ", code=" + code + ", reason=" + reason + ", remote=" + remote);
    }

    /**
     * 服务端绑定成功、开始接受连接时记录一条日志。
     */
    @Override
    public void onStart() {
        DemoEventLog.record("SERVER_ON_START", "服务端已启动，开始接受连接");
    }

    /**
     * 服务端停止完成后记录一条日志。
     */
    @Override
    public void onStop() {
        DemoEventLog.record("SERVER_ON_STOP", "服务端已停止");
    }

    /**
     * 服务器级或会话级运行态故障时记录一条日志。
     *
     * @param session 相关会话；服务器级故障时为 {@code null}
     * @param error 故障
     */
    @Override
    public void onError(WebSocketSession session, Throwable error) {
        DemoEventLog.record("SERVER_ON_ERROR",
                "session=" + (session == null ? null : session.id())
                        + ", " + error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    /**
     * 把收到的消息原样回声回该会话；会话已关闭等运行态故障只记日志，不向外抛。
     *
     * @param session 来源会话
     * @param message 待回声的消息
     */
    private void echo(WebSocketSession session, Message message) {
        try {
            if (message.isText()) {
                session.send(message.asText());
            } else {
                session.send(message.asBytes());
            }
            DemoEventLog.record("SERVER_ECHO", "session=" + session.id() + " 回声完成");
        } catch (RuntimeException e) {
            DemoEventLog.record("SERVER_ECHO_FAIL",
                    "session=" + session.id() + ", " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
