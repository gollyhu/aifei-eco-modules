/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket;

import java.util.Collection;

/**
 * 服务端能力接口：绑定监听端口、接收 WebSocket 连接、管理会话与广播。
 *
 * <p>业务代码只依赖本接口，通过 {@code @Inject} 注入装配好的实例，或用 {@link WebSocketKit} 自行创建；
 * 传输层为内建实现，无可替换后端扩展点。</p>
 *
 * <p>本接口所有方法均可从任意线程调用。但<b>禁止在服务端回调内调用 {@link #stop()}</b>：底层停止会
 * 关闭监听与全部会话，在回调线程内调用可能死锁；如需停止，请投递到外部线程执行。</p>
 */
public interface WebSocketServer {

    /**
     * 绑定并开始接受连接。
     *
     * <p>配置 {@code port = 0} 时由系统分配随机端口，实际端口经 {@link #port()} 读取。绑定成功且
     * 开始接受连接后触发 {@link ServerListener#onStart()}。</p>
     *
     * @throws IllegalStateException 已启动且未停止
     */
    void start();

    /**
     * 停止接受新连接，向全部会话发出关闭帧（关闭码 {@code 1001}，即 RFC6455 的 GOING_AWAY）并释放监听端口；幂等。
     *
     * <p>未启动时为无操作。停止完成后触发 {@link ServerListener#onStop()}。</p>
     *
     * @throws WebSocketException 停止过程中发生运行态故障
     */
    void stop();

    /**
     * 返回是否已启动且未停止。
     *
     * @return 已启动且未停止时 {@code true}
     */
    boolean isStarted();

    /**
     * 返回实际绑定的监听端口。
     *
     * @return 实际端口；未启动时为 {@code 0}
     */
    int port();

    /**
     * 返回当前已登记会话的不可变快照；可能包含正在关闭中的会话。
     *
     * @return 会话快照；不为 {@code null}
     */
    Collection<WebSocketSession> sessions();

    /**
     * 向全部会话广播一条文本消息；对已关闭会话静默跳过，无会话时为无操作。
     *
     * @param text 文本内容
     * @throws IllegalArgumentException {@code text} 为 {@code null}
     */
    void broadcast(String text);

    /**
     * 向全部会话广播一条二进制消息；对已关闭会话静默跳过，无会话时为无操作。
     *
     * @param bytes 二进制内容
     * @throws IllegalArgumentException {@code bytes} 为 {@code null}
     */
    void broadcast(byte[] bytes);

    /**
     * 用服务端配置的编解码器把业务对象编码后广播；对已关闭会话静默跳过。
     *
     * @param payload 业务对象
     * @throws IllegalArgumentException {@code payload} 为 {@code null}
     * @throws IllegalStateException 未配置编解码器
     * @throws WebSocketCodecException 编码失败
     */
    void broadcast(Object payload);

    /**
     * 返回内建传输实现的后端类型自述（固定值，不读配置）。
     *
     * @return 后端类型，恒为 {@code "jdk"}
     */
    String backendType();
}
