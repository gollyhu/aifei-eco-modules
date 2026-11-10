/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.job.demo.job;

/**
 * 演示用的「任务体耗时」工具：把睡眠包装成响应中断的等待。
 *
 * <p>模块停机只发中断信号、不保证强制终止，任务体必须自行检查中断并及时返回——本类是全部演示任务
 * 共用的等待实现，保证「阻塞任务」在 {@code JobPlugin.stop()} 超时后的 {@code shutdownNow()} 中能收敛。</p>
 */
public final class DemoWork {

    /**
     * 禁止创建工具类实例。
     */
    private DemoWork() {
    }

    /**
     * 等待指定时长；被中断时立即返回并<b>恢复中断标志</b>，让调用方按需决定是否提前结束。
     *
     * @param millis 等待毫秒数；{@code <= 0} 时立即返回
     * @return {@code true} 表示等待期间被中断（调用方应尽快返回）
     */
    public static boolean pause(long millis) {
        if (millis <= 0L) {
            return Thread.currentThread().isInterrupted();
        }
        try {
            Thread.sleep(millis);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /**
     * 以固定切片反复等待，总时长不超过 {@code totalMillis}；任一切片被中断即提前结束。
     *
     * <p>用于「长阻塞任务」：切片长度决定响应中断的粒度，不改变总时长语义。</p>
     *
     * @param totalMillis 总等待毫秒数
     * @param sliceMillis 单片等待毫秒数，最小按 1 处理
     * @return {@code true} 表示被中断而提前结束
     */
    public static boolean pauseUpTo(long totalMillis, long sliceMillis) {
        long slice = sliceMillis > 0L ? sliceMillis : 1L;
        long remaining = totalMillis;
        while (remaining > 0L) {
            if (pause(Math.min(slice, remaining))) {
                return true;
            }
            remaining -= slice;
        }
        return false;
    }
}
