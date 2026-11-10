/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import cn.aifei.aop.Singleton;
import cn.aifei.log.Log;
import cn.hg.aifei.job.FixedDelay;

/**
 * 固定延迟演示任务：上次<b>结束</b>后 5 秒再执行。
 *
 * <p>与固定频率的差别在于基准：任务体耗时 500 ms 时，本任务的实际间隔是 5.5 秒（5 秒 + 0.5 秒耗时），
 * 而 {@link DemoFixedRateJob} 的间隔恒为 3 秒。</p>
 */
@Singleton(true)
public class DemoFixedDelayJob {

    /**
     * 任务体耗时（毫秒）：用于对比「以上次结束为基准」的实际间隔。
     */
    private static final long WORK_MILLIS = 500L;

    /**
     * demo 日志通道。
     */
    private static final Log log = Log.get(DemoFixedDelayJob.class);

    /**
     * 固定延迟任务体：睡 500 ms 模拟业务处理。
     */
    @FixedDelay(delay = DemoJobCatalog.FIXED_DELAY_BEAT_MILLIS)
    public void flush() {
        boolean interrupted = DemoWork.pause(WORK_MILLIS);
        log.info("DemoFixedDelayJob#flush 执行完成，耗时约 " + WORK_MILLIS + " ms"
                + (interrupted ? "（收到停机中断，提前返回）" : ""));
    }
}
