/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

/**
 * 客户端能力接口：主动向第三方 WebSocket 服务发起并维持一条出站连接，可选心跳保活与自动重连。
 *
 * <p>业务代码只依赖本接口，通过 {@code @Inject} 注入装配好的实例，或用 {@link WebSocketKit} 自行创建；
 * 换传输后端只改配置 {@code websocket.client.type}，不改调用代码。</p>
 *
 * <p>本接口所有方法均可从任意线程调用。连接结果与断线、重连事件经 {@link ClientListener} 回调；
 * 本接口不提供阻塞等待方法，需要同步等待时由调用方借助 {@link ClientListener} 与同步器自行实现。</p>
 */
public interface WebSocketClient {

    /**
     * 异步发起连接；立即返回，建连结果经 {@link ClientListener} 回调。
     *
     * <p>状态迁移为 {@code CLOSED → CONNECTING}：握手成功后 {@code → OPEN} 并触发
     * {@link ClientListener#onOpen()}；握手失败则回到 {@code CLOSED} 并触发
     * {@link ClientListener#onError(Throwable)}。每次调用都使用全新的传输对象。</p>
     *
     * @throws IllegalStateException 当前状态为 {@code CONNECTING}、{@code OPEN} 或 {@code CLOSING}
     */
    void connect();

    /**
     * 异步、幂等地发起正常关闭；关闭码为 {@code 1000}。
     *
     * <p>状态迁移为 {@code → CLOSING → CLOSED}，关闭完成后触发 {@link ClientListener#onClose(int, String, boolean)}。
     * 当前状态为 {@code CLOSED} 时为无操作；为 {@code CONNECTING} 时中止本次建连并回到 {@code CLOSED}。
     * 本方法置「用户主动关闭」标记并终止一切待定重连，之后不再触发自动重连。</p>
     */
    void close();

    /**
     * 异步、幂等地按指定关闭码与原因发起关闭；语义同 {@link #close()}。
     *
     * @param code 关闭码
     * @param reason 关闭原因
     * @throws IllegalArgumentException {@code code} 不在 {@code [1000, 4999]}
     */
    void close(int code, String reason);

    /**
     * 发送一条文本消息。
     *
     * @param text 文本内容
     * @throws IllegalArgumentException {@code text} 为 {@code null}
     * @throws IllegalStateException 当前状态非 {@code OPEN}；消息不进任何队列、被丢弃
     */
    void send(String text);

    /**
     * 发送一条二进制消息。
     *
     * @param bytes 二进制内容
     * @throws IllegalArgumentException {@code bytes} 为 {@code null}
     * @throws IllegalStateException 当前状态非 {@code OPEN}；消息不进任何队列、被丢弃
     */
    void send(byte[] bytes);

    /**
     * 用配置的编解码器把业务对象编码后发送。
     *
     * @param payload 业务对象
     * @throws IllegalArgumentException {@code payload} 为 {@code null}
     * @throws IllegalStateException 未配置编解码器，或当前状态非 {@code OPEN}
     * @throws WebSocketCodecException 编码失败
     */
    void send(Object payload);

    /**
     * 返回当前连接状态。
     *
     * @return 当前连接状态；新建实例为 {@code CLOSED}
     */
    ConnectionState state();

    /**
     * 返回连接是否已建立且可收发。
     *
     * @return {@code state() == ConnectionState.OPEN} 时 {@code true}
     */
    boolean isOpen();

    /**
     * 返回远端地址的字符串形式（如 {@code 192.0.2.10:54321}）。
     *
     * @return 远端地址；未连接时 {@code null}
     */
    String remoteAddress();

    /**
     * 返回后端类型自述（配置 {@code type}）。
     *
     * @return 后端类型，如 {@code "javawebsocket"}
     */
    String backendType();
}
