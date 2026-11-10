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
 * 超时任务：节拍 2 秒、任务体耗时 4 秒，用于观测 fixedRate 超时跳过（{@code SKIPPED}）。
 *
 * <p>执行结束时 {@code period} 已过：模块记一条 {@code SKIPPED}（事件时间取原计划触发时刻
 * {@code lastStart + period}，不是当前时刻），并以当前时刻为新基准重排，已过的触发点<b>不补跑</b>。
 * 因此该任务在事件流里呈现「{@code STARTED → SKIPPED}」交替，{@code state()} 里的终态长期是 {@code SKIPPED}。</p>
 */
@Singleton(true)
public class DemoSlowJob {

    /**
     * 任务体耗时（毫秒）：大于节拍 2000 ms，必然触发一次跳过记账。
     */
    private static final long WORK_MILLIS = 4000L;

    /**
     * demo 日志通道。
     */
    private static final Log log = Log.get(DemoSlowJob.class);

    /**
     * 超时任务体：睡 4 秒，等待期间可被停机中断提前唤醒。
     */
    @FixedRate(period = DemoJobCatalog.SLOW_PERIOD_MILLIS)
    public void slow() {
        boolean interrupted = DemoWork.pause(WORK_MILLIS);
        log.info("DemoSlowJob#slow 执行结束，耗时约 " + WORK_MILLIS + " ms（> 节拍 2000 ms，本轮记 SKIPPED）"
                + (interrupted ? "，等待期间收到停机中断" : ""));
    }
}
