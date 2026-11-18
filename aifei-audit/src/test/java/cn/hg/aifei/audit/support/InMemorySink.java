/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.support;

import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.spi.AuditSink;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 内存落点：多实例对照时用（A / B 两个落点各自计数）。
 */
public final class InMemorySink implements AuditSink {

    private final String name;

    private final List<AuditEvent> received = Collections.synchronizedList(new ArrayList<AuditEvent>());

    public InMemorySink() {
        this("in-memory");
    }

    public InMemorySink(String name) {
        this.name = name;
    }

    @Override
    public void accept(AuditEvent event) {
        received.add(event);
    }

    public String name() {
        return name;
    }

    public int count() {
        return received.size();
    }

    public List<AuditEvent> events() {
        synchronized (received) {
            return new ArrayList<AuditEvent>(received);
        }
    }

    public void reset() {
        received.clear();
    }
}
