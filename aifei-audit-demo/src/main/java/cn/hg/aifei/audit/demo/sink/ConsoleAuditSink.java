/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.sink;

import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.demo.feed.EventFeed;
import cn.hg.aifei.audit.demo.feed.SinkCounters;
import cn.hg.aifei.audit.spi.AuditSink;

/**
 * 控制台落点：打印一行摘要（示例工程允许标准输出，库模块禁止）。
 */
public final class ConsoleAuditSink implements AuditSink {

    @Override
    public void accept(AuditEvent event) {
        SinkCounters.get().console();
        EventFeed.get().append(event);
        System.out.println("[aifei-audit-demo][console-sink] " + event);
    }
}
