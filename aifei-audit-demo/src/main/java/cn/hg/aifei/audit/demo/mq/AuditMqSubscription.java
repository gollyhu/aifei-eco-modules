/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.mq;

import cn.hg.aifei.audit.demo.feed.SinkCounters;
import cn.hg.aifei.mq.ConsumerKit;
import cn.hg.aifei.mq.Message;
import cn.hg.aifei.mq.MessageListener;

/**
 * mq 消费端：订阅审计主题并计数，用来证明「mq 落点确实投递过」。
 */
public final class AuditMqSubscription implements MessageListener {

    /**
     * 审计事件主题。
     */
    public static final String TOPIC = "aifei.audit.events";

    /**
     * 订阅（必须在 mq 插件启动后调用）。
     */
    public static void subscribe() {
        ConsumerKit.subscribe(TOPIC, new AuditMqSubscription());
    }

    @Override
    public void onMessage(Message message) {
        SinkCounters.get().mqConsumed();
    }
}
