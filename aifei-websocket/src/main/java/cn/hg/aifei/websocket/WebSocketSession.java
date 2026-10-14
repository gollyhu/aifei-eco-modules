/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

/**
 * 服务端侧单条已建立的连接：标识、状态、收发与关闭。
 *
 * <p>会话状态语义与 {@link WebSocketClient} 同源，共用 {@link ConnectionState}。服务端侧不做自动重连：
 * 会话断开即断开。本接口所有方法均可从任意线程调用，也允许在服务端回调内调用（回声是推荐写法）。</p>
 */
public interface WebSocketSession {

    /**
     * 返回会话标识：本服务端实例内唯一、连接存续期间稳定；生成格式不承诺。
     *
     * @return 会话标识
     */
    String id();

    /**
     * 返回该会话的连接状态。
     *
     * @return 连接状态
     */
    ConnectionState state();

    /**
     * 返回该会话是否可收发。
     *
     * @return {@code state() == ConnectionState.OPEN} 时 {@code true}
     */
    boolean isOpen();

    /**
     * 返回远端地址的字符串形式。
     *
     * @return 远端地址；不可用时 {@code null}
     */
    String remoteAddress();

    /**
     * 发送一条文本消息。
     *
     * @param text 文本内容
     * @throws IllegalArgumentException {@code text} 为 {@code null}
     * @throws IllegalStateException 当前状态非 {@code OPEN}
     */
    void send(String text);

    /**
     * 发送一条二进制消息。
     *
     * @param bytes 二进制内容
     * @throws IllegalArgumentException {@code bytes} 为 {@code null}
     * @throws IllegalStateException 当前状态非 {@code OPEN}
     */
    void send(byte[] bytes);

    /**
     * 用服务端配置的编解码器把业务对象编码后发送。
     *
     * @param payload 业务对象
     * @throws IllegalArgumentException {@code payload} 为 {@code null}
     * @throws IllegalStateException 未配置编解码器，或当前状态非 {@code OPEN}
     * @throws WebSocketCodecException 编码失败
     */
    void send(Object payload);

    /**
     * 幂等地关闭该会话；语义同 {@link WebSocketClient#close()}。
     */
    void close();

    /**
     * 幂等地按指定关闭码与原因关闭该会话；语义同 {@link WebSocketClient#close(int, String)}。
     *
     * @param code 关闭码
     * @param reason 关闭原因
     * @throws IllegalArgumentException {@code code} 不在 {@code [1000, 4999]}
     */
    void close(int code, String reason);
}
