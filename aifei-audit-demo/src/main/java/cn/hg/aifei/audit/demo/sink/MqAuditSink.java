/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.sink;

import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.demo.feed.EventFeed;
import cn.hg.aifei.audit.demo.feed.SinkCounters;
import cn.hg.aifei.audit.demo.mq.AuditMqSubscription;
import cn.hg.aifei.audit.spi.AuditSink;
import cn.hg.aifei.mq.ProducerKit;

/**
 * mq 落点：经 aifei-mq（内存后端）投递一行事件文本，由 demo 侧订阅端计数。
 */
public final class MqAuditSink implements AuditSink {

    @Override
    public void accept(AuditEvent event) {
        SinkCounters.get().mq();
        EventFeed.get().append(event);
        ProducerKit.send(AuditMqSubscription.TOPIC, event.toString());
    }
}
