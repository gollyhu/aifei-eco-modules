/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.mq.demo.mq;

import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;

/**
 * 页面订阅专用监听器：与装配期的 {@link DemoMessageListener} 是<b>不同实例</b>（基线硬约束 9）。
 *
 * <p>二者不复用同一个对象，是因为 {@code Consumer.unsubscribe} 按 listener 实例身份移除：若页面复用了装配期
 * 监听器，一次「取消订阅」就会把装配期订阅一并摘掉。代价是同一主题上两条订阅会各记录一次、{@code delivered}
 * 叠加——这是有意保留的行为，日志行的 {@code subscriber} 字段如实区分两者。</p>
 */
public class PageDemoListener implements MessageListener {

    /**
     * 订阅者名称；写入消息记录，用于在日志里区分「本页」与「装配期」的订阅者。
     */
    public static final String NAME = "PageDemoListener";

    /**
     * 本实例的消息记录分片，构造时定死，回调线程上只写这一处。
     */
    private final DemoMessageLog log;

    /**
     * 创建监听器。
     *
     * @param instanceName 页面当前操作的实例名
     * @throws IllegalArgumentException 实例名为空白
     */
    public PageDemoListener(String instanceName) {
        this.log = DemoMessageLog.of(instanceName);
    }

    /**
     * 接收一条消息并记入所属实例的分片；运行期不抛异常（总线会就地隔离并静默丢弃监听器异常）。
     *
     * @param message 收到的消息；永不为 {@code null}
     */
    @Override
    public void onMessage(Message message) {
        log.record(NAME, message);
    }
}
