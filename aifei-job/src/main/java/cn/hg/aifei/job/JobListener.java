/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job;

/**
 * 任务事件监听器（函数式接口）：模块每产生一个 {@link JobEvent} 就同步回调一次。
 *
 * <p><b>同步回调、运行在产生该事件的线程上</b>：不提供异步派发，无监听队列与监听线程池。因此实现必须
 * 快速返回、不做阻塞 IO / 长计算、不依赖线程局部变量，并保证线程安全（不同任务的事件可并发到达同一实例）。
 * 同一任务的事件按 {@link JobEvent.Phase#STARTED} → 终态的顺序串行到达。回调内抛出的异常被捕获记录，
 * 不影响任务执行、结果记账与后续触发。</p>
 *
 * <p>监听器是可选的单例：模块启动时从容器按类型取一次并长期持有，容器中没有实现即视为「无监听器」（不报错，
 * 只是没有任何事件送达）；若容器把该类型注册为多例，则启动失败。慢监听器会拖慢全部任务的到点触发。</p>
 */
@FunctionalInterface
public interface JobListener {

    /**
     * 处理一个任务事件。
     *
     * @param event 事件快照；不为 {@code null}
     */
    void onEvent(JobEvent event);
}
