/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.audit.internal;

/**
 * 时钟采样唯一实现点（非承诺 API）：发生时刻取 epoch 毫秒，耗时取单调时钟差，不受系统时间回调影响。
 */
public final class Clock {

    private static final long NANOS_PER_MILLI = 1000000L;

    private Clock() {
    }

    /**
     * @return 当前 epoch 毫秒
     */
    public static long epochMillis() {
        return System.currentTimeMillis();
    }

    /**
     * @return 单调时钟起点（纳秒）
     */
    public static long startNanos() {
        return System.nanoTime();
    }

    /**
     * @param startNanos {@link #startNanos()} 的返回值
     * @return 已耗时毫秒，负数归一为 {@code 0}
     */
    public static long elapsedMillis(long startNanos) {
        long elapsed = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
        return elapsed < 0L ? 0L : elapsed;
    }
}
