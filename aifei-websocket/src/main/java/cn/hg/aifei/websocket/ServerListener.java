/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

/**
 * 服务端回调：服务器级事件与随会话传入的会话级事件。
 *
 * <p>会话级回调一律随 {@link WebSocketSession} 参数传入，不另立会话监听接口。回调在传输层的工作线程
 * 上执行：回调内可以安全地执行 {@code session.send(...)}（回声是推荐写法），但不得做耗时阻塞操作，
 * 也不得在回调内调用 {@link WebSocketServer#stop()}。回调抛出的异常会被回调保护捕获并转投
 * {@link #onError(WebSocketSession, Throwable)}，不会中断传输线程。</p>
 */
public interface ServerListener {

    /**
     * 新会话登记完成、可收发后触发一次。
     *
     * @param session 新会话
     */
    void onOpen(WebSocketSession session);

    /**
     * 某会话收到一条数据帧时触发。
     *
     * @param session 来源会话
     * @param message 收到的消息；永不为 {@code null}
     */
    void onMessage(WebSocketSession session, Message message);

    /**
     * 会话注销前触发一次；此时 {@code session} 仍可读取 {@link WebSocketSession#id()} 与
     * {@link WebSocketSession#remoteAddress()}，但发送会失败。
     *
     * @param session 被关闭的会话
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote {@code true} 表示由对端发起
     */
    void onClose(WebSocketSession session, int code, String reason, boolean remote);

    /**
     * 服务端绑定成功、开始接受连接时触发；默认空实现。
     */
    default void onStart() {
    }

    /**
     * 服务端停止完成后触发；默认空实现。
     */
    default void onStop() {
    }

    /**
     * 服务器级或会话级运行态故障时触发；默认空实现。
     *
     * @param session 相关会话；服务器级故障时为 {@code null}
     * @param error 故障
     */
    default void onError(WebSocketSession session, Throwable error) {
    }
}
