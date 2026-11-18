/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.support;

import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.spi.AuditSink;

/**
 * 恒抛落点：记录被调用次数与异常实例，不收集事件。
 */
public final class FailingSink implements AuditSink {

    private final RuntimeException failure;

    private int attempts;

    public FailingSink() {
        this(new IllegalStateException("sink-failure"));
    }

    public FailingSink(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public void accept(AuditEvent event) {
        attempts++;
        throw failure;
    }

    public int attempts() {
        return attempts;
    }

    public RuntimeException failure() {
        return failure;
    }
}
