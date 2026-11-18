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
 * 计数 + 收集型落点；可配「先记录再抛」，用于断言「先交付、后上抛」的顺序。
 */
public final class CountingSink implements AuditSink {

    private final List<AuditEvent> received = Collections.synchronizedList(new ArrayList<AuditEvent>());

    private final RuntimeException failure;

    public CountingSink() {
        this(null);
    }

    public CountingSink(RuntimeException failure) {
        this.failure = failure;
    }

    /**
     * @param failure 交付时抛出的异常
     * @return 先记录再抛出该异常的落点
     */
    public static CountingSink recordingThenFailing(RuntimeException failure) {
        return new CountingSink(failure);
    }

    @Override
    public void accept(AuditEvent event) {
        received.add(event);
        if (failure != null) {
            throw failure;
        }
    }

    public int count() {
        return received.size();
    }

    public List<AuditEvent> events() {
        synchronized (received) {
            return new ArrayList<AuditEvent>(received);
        }
    }

    public AuditEvent first() {
        List<AuditEvent> all = events();
        return all.isEmpty() ? null : all.get(0);
    }

    public AuditEvent last() {
        List<AuditEvent> all = events();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    public void reset() {
        received.clear();
    }
}
