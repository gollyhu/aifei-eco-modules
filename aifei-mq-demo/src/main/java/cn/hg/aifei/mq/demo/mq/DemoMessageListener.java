/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;

/**
 * 演示用的消息监听器：把订阅到的消息写入 {@link DemoMessageLog}。
 *
 * <p>回调由后端在生产者调用线程内直接执行（本模块为同步投递），运行期不抛异常——总线会就地隔离并静默
 * 丢弃监听器异常，故这里只做无副作用的记录。{@link Message} 只提供 {@code topic()} 与字节 {@code payload()}，
 * 文本需自行按 UTF-8 解码（解码在日志类内完成）。</p>
 */
public class DemoMessageListener implements MessageListener {

    /**
     * 订阅者名称；写入消息记录，用于区分同一主题上的多个订阅者。
     */
    public static final String NAME = "DemoMessageListener";

    /**
     * 创建监听器；无状态，可安全地被消费者持有。
     */
    public DemoMessageListener() {
    }

    /**
     * 接收一条消息并记入演示日志。
     *
     * @param message 收到的消息；永不为 {@code null}
     */
    @Override
    public void onMessage(Message message) {
        DemoMessageLog.record(NAME, message);
    }
}
