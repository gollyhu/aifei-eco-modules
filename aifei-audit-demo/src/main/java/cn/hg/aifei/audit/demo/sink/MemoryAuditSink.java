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
 * 内存落点：写入页面读模型 + 计数。
 */
public final class MemoryAuditSink implements AuditSink {

    @Override
    public void accept(AuditEvent event) {
        SinkCounters.get().memory();
        EventFeed.get().append(event);
    }
}
