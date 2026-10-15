/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

/**
 * 客户端回调：连接结果、收到的消息、断线以及自动重连事件。
 *
 * <p>回调在传输层的读 / 连接线程上执行：回调内可以安全地再调 {@code send} / {@code close}，
 * 但不得做耗时阻塞操作，也不得在回调内调 {@link WebSocketClient#connect()}。回调抛出的异常会被
 * 回调保护捕获并转投 {@link #onError(Throwable)}，不会中断传输线程。</p>
 */
public interface ClientListener {

    /**
     * 连接（含重连）建立成功时触发一次；每次成功建连都会触发。
     */
    void onOpen();

    /**
     * 收到一条文本或二进制数据帧时触发。
     *
     * @param message 收到的消息；永不为 {@code null}
     */
    void onMessage(Message message);

    /**
     * 连接关闭时触发一次；不论是否重连、不论用户主动还是异常，都恰好触发一次。
     *
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote {@code true} 表示由对端发起
     */
    void onClose(int code, String reason, boolean remote);

    /**
     * 连接失败、传输 IO 错误或协议错误等运行态故障时触发。
     *
     * @param error 故障；为底层异常包装后的 {@link WebSocketConnectionException}，不外泄第三方类型
     */
    void onError(Throwable error);

    /**
     * 即将进行第 {@code attempt} 次自动重连且已算出退避时长时触发；默认空实现。
     *
     * @param attempt 重连次数，从 {@code 1} 起
     * @param delayMillis 本次重连的退避时长（毫秒）
     */
    default void onReconnect(int attempt, long delayMillis) {
    }

    /**
     * 连续重连仍失败、放弃重连时触发一次；默认空实现。
     *
     * @param attempts 已连续失败的次数
     * @param lastError 最后一次失败的原因
     */
    default void onReconnectExhausted(int attempts, Throwable lastError) {
    }
}
