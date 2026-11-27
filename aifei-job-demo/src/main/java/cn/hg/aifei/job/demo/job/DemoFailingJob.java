/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import cn.aifei.aop.Singleton;
import cn.aifei.log.Log;
import cn.hg.aifei.job.FixedRate;

/**
 * 异常任务：每 4 秒触发且<b>必定抛异常</b>，用于观测异常就地隔离。
 *
 * <p>模块对任务体异常的口径：捕获并记录堆栈（含 {@code Error}），不做重试，<b>续排不受影响</b>——因此本任务的
 * 触发时间点会一直推进，其他任务的节拍也照常。{@code JobKit.state()} 里它保留 {@code FAILED}，事件流里可见
 * {@code STARTED → FAILED} 成对出现。</p>
 */
@Singleton(true)
public class DemoFailingJob {

    /**
     * demo 日志通道。
     */
    private static final Log log = Log.get(DemoFailingJob.class);

    /**
     * 异常任务体：先留下一条日志，再抛 {@link IllegalStateException}。
     */
    @FixedRate(period = DemoJobCatalog.FAILING_PERIOD_MILLIS)
    public void boom() {
        log.info("DemoFailingJob#boom 即将抛出模拟异常");
        throw new IllegalStateException("模拟下游库存服务连续 3 次超时（本任务每轮必抛）");
    }
}
