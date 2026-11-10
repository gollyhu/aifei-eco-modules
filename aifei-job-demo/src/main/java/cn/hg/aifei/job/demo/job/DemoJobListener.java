/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

import cn.aifei.aop.Singleton;
import cn.hg.aifei.job.JobEvent;
import cn.hg.aifei.job.JobListener;

/**
 * demo 的任务事件监听器：把每个 {@link JobEvent} 记进 {@link DemoEventLog}，供端点回放。
 *
 * <p><b>必须注册进容器</b>（{@code DemoAppConfig} 里按 {@link JobListener} 类型注册）：模块启动时按类型取一次，
 * 未注册则任务照常触发但一条事件都不会送达。回调是<b>同步</b>的、运行在产生事件的线程上，所以本类只做一件事——
 * 线程安全地写一个内存环形缓冲，<b>不做阻塞 IO、不做长计算</b>，否则会拖慢全部任务的到点唤醒。</p>
 */
@Singleton(true)
public class DemoJobListener implements JobListener {

    /**
     * 处理一个任务事件：记录任务名、阶段、触发时间、异常摘要与当前线程名。
     *
     * @param event 事件快照；为 {@code null} 时直接忽略（框架不会传 {@code null}，此处仅作防御）
     */
    @Override
    public void onEvent(JobEvent event) {
        if (event == null) {
            return;
        }
        String phase = event.phase() == null ? "UNKNOWN" : event.phase().name();
        DemoEventLog.record(event.taskName(), phase, event.triggerTime(),
                DemoFormats.errorText(event.error()), Thread.currentThread().getName());
    }
}
