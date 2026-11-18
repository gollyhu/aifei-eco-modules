/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.spi;

import cn.hg.aifei.audit.AuditEvent;

/**
 * 落点：接管单条事件。实现须线程安全、不得抛受检异常、不得回调业务方法（避免递归采集）。
 */
public interface AuditSink {

    /**
     * 接收一条已构造完成的事件；调用发生在业务方法所在线程（同步直写，不排队、不重试）。
     *
     * @param event 已构造完成、不可变的事件；不为 null
     */
    void accept(AuditEvent event);
}
