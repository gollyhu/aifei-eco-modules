/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.demo.sink;

import cn.hg.aifei.audit.AuditEvent;
import cn.hg.aifei.audit.demo.feed.SinkCounters;
import cn.hg.aifei.audit.spi.AuditSink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可切换委托落点：容器内始终只有它一个 {@link AuditSink}，切换只改内部委托。
 *
 * <p>框架拒绝同类型二次注册，运行期也无法换实现，故「落点切换」只能由消费方以委托形态实现。</p>
 */
public final class SwitchableAuditSink implements AuditSink {

    /**
     * 内存落点名。
     */
    public static final String MEMORY = "memory";

    /**
     * 控制台落点名。
     */
    public static final String CONSOLE = "console";

    /**
     * mq 落点名。
     */
    public static final String MQ = "mq";

    private static volatile SwitchableAuditSink instance;

    private final Map<String, AuditSink> delegates = new LinkedHashMap<String, AuditSink>();

    private volatile String current = MEMORY;

    private volatile AuditSink delegate;

    private volatile boolean failMode;

    private SwitchableAuditSink(AuditSink memory, AuditSink console, AuditSink mq) {
        delegates.put(MEMORY, memory);
        delegates.put(CONSOLE, console);
        delegates.put(MQ, mq);
        this.delegate = memory;
    }

    /**
     * 创建（并由容器持有）唯一实例。
     *
     * @param memory  内存落点
     * @param console 控制台落点
     * @param mq      mq 落点
     * @return 唯一实例
     */
    public static synchronized SwitchableAuditSink create(AuditSink memory, AuditSink console, AuditSink mq) {
        instance = new SwitchableAuditSink(memory, console, mq);
        return instance;
    }

    /**
     * @return 唯一实例；装配完成前为 {@code null}
     */
    public static SwitchableAuditSink get() {
        return instance;
    }

    /**
     * @return 可选落点名列表
     */
    public static List<String> names() {
        List<String> names = new ArrayList<String>();
        names.add(MEMORY);
        names.add(CONSOLE);
        names.add(MQ);
        return names;
    }

    @Override
    public void accept(AuditEvent event) {
        SinkCounters.get().delivered();
        if (failMode) {
            throw new IllegalStateException("demo-sink-failure");
        }
        delegate.accept(event);
    }

    /**
     * @param name 目标落点名
     * @return 名字非法时为 {@code false}
     */
    public boolean switchTo(String name) {
        AuditSink target = name == null ? null : delegates.get(name);
        if (target == null) {
            return false;
        }
        delegate = target;
        current = name;
        return true;
    }

    public String current() {
        return current;
    }

    public boolean failMode() {
        return failMode;
    }

    /**
     * @param on 打开后每次交付先抛异常，用于演示失败隔离
     */
    public void failMode(boolean on) {
        this.failMode = on;
    }
}
