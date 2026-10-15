/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq;

/**
 * 消息监听器：订阅主题后由后端回调。
 */
@FunctionalInterface
public interface MessageListener {

    /**
     * 接收一条消息。
     *
     * @param message 消息
     */
    void onMessage(Message message);
}
