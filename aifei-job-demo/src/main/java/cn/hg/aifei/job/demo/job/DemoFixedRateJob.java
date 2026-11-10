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
 * 固定频率演示任务：每 3 秒触发一次，首次延迟 2 秒。
 *
 * <p>演示「{@code initialDelay} 只影响首次」：首触发在启动 2 秒后，之后以<b>上次开始</b>为基准每 3 秒一次；
 * 任务体耗时 300 ms 远小于节拍，故不会出现超时跳过。</p>
 */
@Singleton(true)
public class DemoFixedRateJob {

    /**
     * 任务体耗时（毫秒）：远小于节拍，保证本轮按时结束后续排。
     */
    private static final long WORK_MILLIS = 300L;

    /**
     * demo 日志通道。
     */
    private static final Log log = Log.get(DemoFixedRateJob.class);

    /**
     * 固定频率任务体：睡 300 ms 模拟业务处理。
     */
    @FixedRate(period = DemoJobCatalog.FIXED_RATE_PERIOD_MILLIS,
            initialDelay = DemoJobCatalog.FIXED_RATE_INITIAL_DELAY_MILLIS)
    public void poll() {
        boolean interrupted = DemoWork.pause(WORK_MILLIS);
        log.info("DemoFixedRateJob#poll 执行完成，耗时约 " + WORK_MILLIS + " ms"
                + (interrupted ? "（收到停机中断，提前返回）" : ""));
    }
}
