/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.aifei.websocket.demo.ws;

import cn.aifei.websocket.ClientListener;
import cn.aifei.websocket.Message;

/**
 * 客户端监听器的演示实现：把建连、收发、断线与自动重连事件追加进 {@link DemoEventLog}，供演示端点读出去。
 *
 * <p>回调在底层库的读 / 连接线程上执行，因此这里只做「记一行日志」这种极轻量操作，不做耗时阻塞。</p>
 */
public class DemoClientListener implements ClientListener {

    /**
     * 创建监听器；无状态，可安全地被单个客户端实例持有。
     */
    public DemoClientListener() {
    }

    /**
     * 每次成功建连（含自动重连成功）时记录一条日志。
     */
    @Override
    public void onOpen() {
        DemoEventLog.record("CLIENT_ON_OPEN", "建连成功");
    }

    /**
     * 收到一条数据帧时记录一条日志。
     *
     * @param message 收到的消息；永不为 {@code null}
     */
    @Override
    public void onMessage(Message message) {
        if (message.isText()) {
            DemoEventLog.record("CLIENT_ON_MESSAGE", "收到文本: " + message.asText());
        } else {
            DemoEventLog.record("CLIENT_ON_MESSAGE", "收到二进制 " + message.size() + " 字节");
        }
    }

    /**
     * 连接关闭时记录一条日志。
     *
     * @param code 关闭码
     * @param reason 关闭原因
     * @param remote {@code true} 表示由对端发起
     */
    @Override
    public void onClose(int code, String reason, boolean remote) {
        DemoEventLog.record("CLIENT_ON_CLOSE",
                "code=" + code + ", reason=" + reason + ", remote=" + remote);
    }

    /**
     * 运行态故障时记录一条日志。
     *
     * @param error 故障；已包装为 {@code WebSocketConnectionException}
     */
    @Override
    public void onError(Throwable error) {
        DemoEventLog.record("CLIENT_ON_ERROR",
                error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    /**
     * 即将发起自动重连时记录一条日志，便于现场看到退避时长。
     *
     * @param attempt 重连次数，从 {@code 1} 起
     * @param delayMillis 本次退避时长（毫秒）
     */
    @Override
    public void onReconnect(int attempt, long delayMillis) {
        DemoEventLog.record("CLIENT_ON_RECONNECT",
                "第 " + attempt + " 次重连，退避 " + delayMillis + " 毫秒");
    }

    /**
     * 连续重连失败、放弃重连时记录一条日志。
     *
     * @param attempts 已连续失败的次数
     * @param lastError 最后一次失败的原因
     */
    @Override
    public void onReconnectExhausted(int attempts, Throwable lastError) {
        DemoEventLog.record("CLIENT_ON_RECONNECT_EXHAUSTED",
                "连续失败 " + attempts + " 次，放弃重连，lastError=" + lastError);
    }
}
