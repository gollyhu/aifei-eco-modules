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
 * 长阻塞任务：每次最多阻塞 6 秒，用于观测停机收敛（观测点 5「等待中 / 已中断」）。
 *
 * <p>模块停机顺序固定为「停止新触发 → 等待在途任务至 {@code job.shutdown.timeoutMillis} → 超时
 * {@code shutdownNow()}」。本任务以 200 ms 切片反复等待并<b>检查中断标志</b>：收到中断即提前返回，这正是
 * 模块对任务体的要求（只发中断信号，不保证强制终止）。</p>
 *
 * <p>本 demo 不真的停应用（停机信息端点为只读展示），故阻塞时长设有上界，避免长期占住执行线程；把
 * {@code start-demo.bat} 里的进程停掉即可观察 JVM 退出时 daemon 线程的行为。</p>
 */
@Singleton(true)
public class DemoShutdownJob {

    /**
     * 单次最长阻塞时长（毫秒）。
     */
    private static final long BLOCK_MILLIS = 6000L;

    /**
     * 中断检查粒度（毫秒）。
     */
    private static final long SLICE_MILLIS = 200L;

    /**
     * demo 日志通道。
     */
    private static final Log log = Log.get(DemoShutdownJob.class);

    /**
     * 阻塞任务体：最多阻塞 6 秒，每 200 ms 检查一次中断。
     */
    @FixedDelay(delay = DemoJobCatalog.SHUTDOWN_DELAY_MILLIS)
    public void waiting() {
        boolean interrupted = DemoWork.pauseUpTo(BLOCK_MILLIS, SLICE_MILLIS);
        log.info("DemoShutdownJob#waiting 结束阻塞，最长 " + BLOCK_MILLIS + " ms / 切片 " + SLICE_MILLIS + " ms"
                + (interrupted ? "（收到停机中断，提前收敛）" : ""));
    }
}
