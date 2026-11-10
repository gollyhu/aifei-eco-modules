/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import cn.aifei.aop.Singleton;
import cn.aifei.log.Log;
import cn.hg.aifei.job.Cron;
import cn.hg.aifei.job.JobEvent;

/**
 * cron 演示任务：表达式自身定义触发点（每 5 秒），用于观测「到点触发」。
 *
 * <p>方法签名取 {@code (JobEvent)} 形式：参数是本次触发的 {@code STARTED} 快照，可直接拿到触发时刻；
 * 写无参方法同样合法（模块两种签名都接受）。</p>
 */
@Singleton(true)
public class DemoCronJob {

    /**
     * demo 日志通道（按 aifei 框架 {@code Log} 记录，日志实现由装配期配置）。
     */
    private static final Log log = Log.get(DemoCronJob.class);

    /**
     * 每 5 秒触发一次，任务体只做一次日志记录（微秒级返回，不影响其他任务的到点唤醒）。
     *
     * @param started 本次触发的 STARTED 快照
     */
    @Cron(DemoJobCatalog.CRON_EXPRESSION)
    public void tick(JobEvent started) {
        long triggerTime = started == null ? System.currentTimeMillis() : started.triggerTime();
        log.info("DemoCronJob#tick 到点触发，triggerTime = " + DemoFormats.clockText(triggerTime));
    }
}
