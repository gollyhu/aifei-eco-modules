/*
 * Copyright 2014-2026 糊搞 (3722711@qq.com)
 *
 * https://gitee.com/gollyhu
 */

package cn.hg.aifei.websocket.internal;

/**
 * 指数退避与抖动的纯函数定义点：不依赖时钟、不依赖随机源，随机数由调用方以 {@code r} 传入，便于独立单测。
 *
 * <p>计算式为 {@code base = initial * 2^(attempt-1)}，{@code capped = min(base, max)}，
 * {@code factor = 1 - jitter + 2 * jitter * r}，返回 {@code round(capped * factor)}。结果下界为
 * {@code capped * (1 - jitter)}、上界为 {@code capped * (1 + jitter)}。</p>
 */
public final class Backoff {

    /**
     * 禁止创建工具类实例。
     */
    private Backoff() {
    }

    /**
     * 计算第 {@code attempt} 次重连的退避时长。
     *
     * @param attempt 重连次数，从 {@code 1} 起
     * @param initial 首次重试延迟（毫秒），须大于 {@code 0}
     * @param max 单次重试延迟上限（毫秒）
     * @param jitter 抖动比例，取值 {@code [0.0, 1.0]}
     * @param r 抖动随机数，取值 {@code [0.0, 1.0)}
     * @return 退避时长（毫秒）
     */
    public static long delayMillis(int attempt, long initial, long max, double jitter, double r) {
        long capped = cappedBase(attempt, initial, max);
        double factor = 1.0 - jitter + 2.0 * jitter * r;
        return Math.round(capped * factor);
    }

    /**
     * 计算未加抖动的指数基数并封顶到 {@code max}。
     *
     * <p>逐次左移一位并检查是否已达上限：一旦 {@code base >= max} 立即返回 {@code max}，避免
     * {@code initial << (attempt-1)} 在大 {@code attempt} 下溢出；另在移位前判断是否会跨过
     * {@link Long#MAX_VALUE} 的高位，作为绝对的溢出兜底。</p>
     *
     * @param attempt 重连次数，从 {@code 1} 起
     * @param initial 首次重试延迟（毫秒）
     * @param max 单次重试延迟上限（毫秒）
     * @return {@code min(initial * 2^(attempt-1), max)} 的等价结果，且无溢出
     */
    private static long cappedBase(int attempt, long initial, long max) {
        long base = initial;
        for (int i = 1; i < attempt; i++) {
            if (base >= max) {
                return max;
            }
            if (base > (Long.MAX_VALUE >> 1)) {
                return max;
            }
            base <<= 1;
        }
        return base >= max ? max : base;
    }
}
